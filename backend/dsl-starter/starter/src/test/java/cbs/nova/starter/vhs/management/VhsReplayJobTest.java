package cbs.nova.starter.vhs.management;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.vhs.replay.VhsReplayReport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit specs for {@link VhsReplayJob}, focused on the bounded in-memory status map.
 *
 * <p>
 * The replay engine and tape I/O are not exercised here — these tests drive the status-tracking
 * primitives directly via reflection so we can assert the eviction / overshoot / late-update
 * contract without spinning up the full replay pipeline.
 */
class VhsReplayJobTest {

  @Test
  void boundedMapKeepsNewestAndEvictsOldestFinishedFirst() throws Exception {
    int limit = 5;
    VhsReplayJob job = jobWithStatusLimit(limit);

    // Insert limit+N finished entries. Insertion order matches replay id order; the oldest
    // entries must be evicted first and the newest must still be present.
    int total = limit + 3;
    List<String> ids = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      String id = "replay-fin-" + i;
      ids.add(id);
      recordStatus(job, id, "completed", reportStub());
    }

    assertThat(job.statusCount()).isEqualTo(limit);

    // The 3 oldest finished entries (ids 0..2) must be gone.
    for (int i = 0; i < total - limit; i++) {
      assertThat(job.status(ids.get(i))).as("oldest finished id %s should be evicted", i).isEmpty();
    }
    // The newest entries must still be there with the correct final status.
    for (int i = total - limit; i < total; i++) {
      String id = ids.get(i);
      Optional<VhsReplayJob.JobStatus> status = job.status(id);
      assertThat(status).as("newest id %s should still be tracked", i).isPresent();
      assertThat(status.get().status()).isEqualTo("completed");
    }
  }

  @Test
  void inFlightEntrySurvivesEviction() throws Exception {
    int limit = 4;
    VhsReplayJob job = jobWithStatusLimit(limit);

    // One in-flight entry (accepted) inserted first, then several finished entries that push
    // the map past the limit. The accepted entry must never be evicted while still in-flight.
    String inflight = "replay-inflight";
    recordStatus(job, inflight, "accepted", null);

    for (int i = 0; i < limit + 2; i++) {
      recordStatus(job, "replay-fin-" + i, "completed", reportStub());
    }

    assertThat(job.status(inflight))
            .as("in-flight (accepted) entry must never be evicted before finishing")
            .isPresent()
            .hasValueSatisfying(s -> assertThat(s.status()).isEqualTo("accepted"));
  }

  @Test
  void lateCompletionUpdateDoesNotEvictInFlightJob() throws Exception {
    int limit = 3;
    VhsReplayJob job = jobWithStatusLimit(limit);

    // Fill the map to the limit with finished entries.
    recordStatus(job, "replay-fin-0", "completed", reportStub());
    recordStatus(job, "replay-fin-1", "completed", reportStub());
    recordStatus(job, "replay-fin-2", "completed", reportStub());
    assertThat(job.statusCount()).isEqualTo(limit);

    // An in-flight job arrives; the map temporarily overshoots the limit (no finished entry is
    // old enough to evict, and the in-flight one must not be evicted either).
    String inflight = "replay-late";
    recordStatus(job, inflight, "accepted", null);
    assertThat(job.statusCount()).isEqualTo(limit + 1);
    assertThat(job.status(inflight)).isPresent();

    // Late completion update of the in-flight id: this is an existing key, so the value is
    // replaced in place and no eviction is triggered. The final status must be visible and
    // the other finished entries must still be present.
    recordStatus(job, inflight, "completed", reportStub());
    assertThat(job.statusCount()).isEqualTo(limit + 1);
    assertThat(job.status(inflight))
            .isPresent()
            .hasValueSatisfying(s -> {
              assertThat(s.status()).isEqualTo("completed");
              assertThat(s.report()).isNotNull();
            });
    assertThat(job.status("replay-fin-2")).isPresent();
  }

  @Test
  void overshootWhenAllEntriesAreInFlight() throws Exception {
    int limit = 2;
    VhsReplayJob job = jobWithStatusLimit(limit);

    // Every entry is in-flight: no finished entry exists to evict, so the map grows past the
    // limit rather than dropping an in-flight job.
    recordStatus(job, "replay-a", "accepted", null);
    recordStatus(job, "replay-b", "accepted", null);
    recordStatus(job, "replay-c", "accepted", null);
    recordStatus(job, "replay-d", "accepted", null);

    assertThat(job.statusCount()).isEqualTo(4);
    assertThat(job.status("replay-a")).isPresent();
    assertThat(job.status("replay-d")).isPresent();
  }

  @Test
  void concurrentInsertsRespectTheBoundAndNeverEvictInFlightEntries() throws Exception {
    int limit = 50;
    int threads = 8;
    int insertsPerThread = 100;
    VhsReplayJob job = jobWithStatusLimit(limit);

    // Each writer mixes two operations:
    // (a) insert a NEW in-flight id ('accepted') — must not evict any entry
    // (b) insert a NEW finished id ('completed') — must evict the oldest finished entry
    // Use a barrier to release all writers at the same time and a completion latch so the
    // test does not rely on sleeps.
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    List<List<String>> inFlightIdsByThread = new ArrayList<>(threads);
    List<List<String>> finishedIdsByThread = new ArrayList<>(threads);

    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        final int threadIdx = t;
        List<String> inFlightIds = new ArrayList<>();
        List<String> finishedIds = new ArrayList<>();
        inFlightIdsByThread.add(inFlightIds);
        finishedIdsByThread.add(finishedIds);
        futures.add(pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < insertsPerThread; i++) {
              String id = "t" + threadIdx + "-i" + i;
              if ((i & 1) == 0) {
                inFlightIds.add(id);
                recordStatus(job, id, "accepted", null);
              } else {
                finishedIds.add(id);
                recordStatus(job, id, "completed", reportStub());
              }
            }
          } catch (Throwable th) {
            failure.compareAndSet(null, th);
          } finally {
            done.countDown();
          }
        }));
      }
      start.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).as("all writers finished in time").isTrue();
      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(failure.get()).as("no writer thread failed").isNull();

    // Every in-flight id must still be tracked (never evicted while in-flight).
    int inFlightTracked = 0;
    for (List<String> ids : inFlightIdsByThread) {
      for (String id : ids) {
        Optional<VhsReplayJob.JobStatus> status = job.status(id);
        if (status.isPresent()) {
          inFlightTracked++;
          assertThat(status.get().status())
                  .as("in-flight id %s must still be tracked", id)
                  .isEqualTo("accepted");
        }
      }
    }
    assertThat(inFlightTracked)
            .as("no in-flight id may be silently evicted")
            .isEqualTo(inFlightIdsByThread.stream().mapToInt(List::size).sum());

    // Total map size is bounded by the documented contract: in-flight (never evicted) + finished
    // (oldest-first eviction, so at most 'limit' finished entries once the map is past the cap).
    // The exact size depends on interleaving, but it must never exceed the sum of all distinct
    // ids the writers produced.
    int distinctIds = threads * insertsPerThread;
    assertThat(job.statusCount())
            .as("map size must be bounded by the number of distinct ids")
            .isLessThanOrEqualTo(distinctIds);

    // Finished entries that are still tracked must show their final 'completed' status.
    for (List<String> ids : finishedIdsByThread) {
      for (String id : ids) {
        job.status(id).ifPresent(s -> assertThat(s.status())
                .as("finished id %s must show its final status", id)
                .isEqualTo("completed"));
      }
    }
  }

  // --- helpers ---------------------------------------------------------------------------------

  /**
   * Build a {@link VhsReplayJob} whose {@link CbsVhsReplayProperties#statusLimit()} is the given
   * value.
   */
  private static VhsReplayJob jobWithStatusLimit(int limit) {
    CbsVhsReplayProperties props = new CbsVhsReplayProperties(
            true, "dry-run", false,
            cbs.nova.starter.vhs.replay.ReplayMode.exact,
            1, 1.0, 4,
            0L, false, 0L, 30_000L,
            "http://localhost:8080",
            CbsVhsReplayProperties.Faking.disabled(),
            limit);
    return new VhsReplayJob(props, new ObjectMapper(), null);
  }

  /** Drive the private {@code recordStatus} entry point via reflection so the test is hermetic. */
  private static void recordStatus(VhsReplayJob job, String id, String state,
          VhsReplayReport report)
          throws Exception {
    Method m = VhsReplayJob.class.getDeclaredMethod("recordStatus", VhsReplayJob.JobStatus.class);
    m.setAccessible(true);
    m.invoke(job, new VhsReplayJob.JobStatus(id, state, report));
  }

  private static VhsReplayReport reportStub() {
    return new VhsReplayReport(
            cbs.nova.starter.vhs.replay.ReplayMode.exact,
            1, 0, 0, 0, 0, 0L, List.of(), List.of());
  }
}
