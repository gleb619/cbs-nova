package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.persistence.DslEventRepository;
import cbs.nova.starter.service.EmptyObjectProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class ExecutionSseServiceTest {

  private ExecutionSseService service;
  private ExecutionSseService cappedService;

  @BeforeEach
  void setUp() {
    service = new ExecutionSseService(new CbsSseProperties(500, 1000),
            EmptyObjectProvider.of(DslEventRepository.class),
            new ObjectMapper());
    cappedService = new ExecutionSseService(new CbsSseProperties(2, 1000),
            EmptyObjectProvider.of(DslEventRepository.class),
            new ObjectMapper());
  }

  @Test
  void subscribeReturnsEmitterAndReplacesPrevious() {
    SseEmitter first = service.subscribe("run-1", 0L);
    assertThat(first).isNotNull();
    assertThat(service.subscriberCount("run-1")).isEqualTo(1);

    SseEmitter second = service.subscribe("run-1", 0L);
    assertThat(second).isNotSameAs(first);
    assertThat(service.subscriberCount("run-1")).isEqualTo(1);
  }

  @Test
  void reSubscribeSameIdDoesNotConsumeAdditionalCap() {
    SseEmitter first = cappedService.subscribe("a", 0L);
    SseEmitter second = cappedService.subscribe("a", 0L);
    SseEmitter third = cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    assertThat(second).isNotSameAs(first);
    assertThat(third).isNotSameAs(first);
    assertThat(cappedService.subscriberCount("a")).isEqualTo(1);
    assertThat(cappedService.subscriberCount("b")).isEqualTo(1);
  }

  @Test
  void subscribeOverCapThrowsAndCreatesNoEmitter() {
    cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);

    assertThatThrownBy(() -> cappedService.subscribe("c", 0L))
            .isInstanceOf(DslSubscriberCapExceededException.class)
            .satisfies(ex -> {
              DslSubscriberCapExceededException e = (DslSubscriberCapExceededException) ex;
              assertThat(e.getCap()).isEqualTo(2);
              assertThat(e.getId()).isEqualTo("c");
            });

    assertThat(cappedService.subscriberCount("c")).isEqualTo(0);
    assertThat(cappedService.activeSubscriberCount())
            .as("failed subscribe must NOT increment counter")
            .isEqualTo(2);
  }

  @Test
  void concurrentSubscribersAtLimitMinusOneNeverBothSucceed() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < 20; i++) {
        CbsSseProperties props = new CbsSseProperties(2, 1000);
        ExecutionSseService s = new ExecutionSseService(props,
                EmptyObjectProvider.of(DslEventRepository.class), new ObjectMapper());
        // Pre-fill cap exactly.
        s.subscribe("pre-a", 0L);
        s.subscribe("pre-b", 0L);
        CountDownLatch ready = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CompletableFuture<?>[] futures = new CompletableFuture[threads];
        for (int t = 0; t < threads; t++) {
          final String id = "race-" + t + "-" + i;
          futures[t] = CompletableFuture.runAsync(() -> {
            ready.countDown();
            try {
              ready.await();
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
            try {
              s.subscribe(id, 0L);
              ok.incrementAndGet();
            } catch (DslSubscriberCapExceededException ex) {
              rejected.incrementAndGet();
            }
          }, pool);
        }
        CompletableFuture.allOf(futures).join();
        assertThat(ok.get()).as("iter %d: no thread should succeed when cap is already met", i)
                .isEqualTo(0);
        assertThat(rejected.get()).isEqualTo(threads);
        assertThat(s.activeSubscriberCount()).isEqualTo(2);
        s.destroy();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void completeFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    invokeDefaultCallback(a, "completionCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    // Slot freed → new id succeeds.
    SseEmitter c = cappedService.subscribe("c", 0L);
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void timeoutFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    // Spring's onTimeout callbacks are stored in a private DefaultCallback on SseEmitter. We
    // invoke that callback list directly to simulate the async container firing the timeout
    // (no public timeout() method exists on SseEmitter).
    invokeDefaultCallback(a, "timeoutCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    SseEmitter c = cappedService.subscribe("c", 0L);
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  private static void invokeDefaultCallback(SseEmitter emitter, String fieldName)
          throws Exception {
    Field f = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredField(fieldName);
    f.setAccessible(true);
    Runnable callback = (Runnable) f.get(emitter);
    callback.run();
  }

  @Test
  void errorFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    // Trigger the onError callback list (without a Handler attached, SseEmitter#completeWithError
    // does not invoke the registered Consumer — call the registered DefaultCallback directly).
    Field f = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredField("errorCallback");
    f.setAccessible(true);
    java.util.function.Consumer<Throwable> cb = (java.util.function.Consumer<Throwable>) f.get(a);
    cb.accept(new IOException("client disconnected"));
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    SseEmitter c = cappedService.subscribe("c", 0L);
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void heartbeatSendFailureFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);

    // Swap `a` with a spy whose send() throws on every call → publish() path triggers the catch
    // arm in sendHeartbeats (or publish) which removes the entry.
    SseEmitter spyA = spy(a);
    doThrow(new IOException("simulated dead socket")).when(spyA)
            .send(any(SseEmitter.SseEventBuilder.class));
    replaceEmitter(cappedService, "a", spyA);

    cappedService.publish("a", "RUNNING");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    assertThat(cappedService.subscriberCount("a")).isEqualTo(0);
  }

  @Test
  void staleCallbackAfterReplacementDoesNotFreeNewerEmitter() throws Exception {
    SseEmitter first = cappedService.subscribe("a", 0L);
    cappedService.subscribe("a", 0L);
    // second replaced first in the map; first's onCompletion callback still references the
    // first emitter instance. Invoking it must NOT decrement the activeCount (newer emitter
    // holds the slot).
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    invokeDefaultCallback(first, "completionCallback");
    assertThat(cappedService.activeSubscriberCount())
            .as("stale callback must not free newer emitter's slot")
            .isEqualTo(1);
    assertThat(cappedService.subscriberCount("a")).isEqualTo(1);
  }

  @Test
  void destroyShutsDownHeartbeatExecutor() throws Exception {
    Field field = ExecutionSseService.class.getDeclaredField("heartbeatExecutor");
    field.setAccessible(true);
    ScheduledExecutorService exec = (ScheduledExecutorService) field.get(service);
    assertThat(exec.isShutdown()).isFalse();
    service.destroy();
    assertThat(exec.isShutdown()).isTrue();
    // Idempotent: second destroy must not throw.
    service.destroy();
  }

  @Test
  void publishToUnknownRunIsNoOp() {
    service.publish("missing", "COMPLETED");
    assertThat(service.subscriberCount("missing")).isEqualTo(0);
  }

  @Test
  void publishSendsStatusEventWithDisplayCasing() throws Exception {
    SseEmitter emitter = service.subscribe("run-1", 0L);
    SseEmitter spy = spy(emitter);
    doNothing().when(spy).send(any(SseEmitter.SseEventBuilder.class));
    replaceEmitter(service, "run-1", spy);

    service.publish("run-1", "COMPLETED");

    verify(spy).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void noLastEventIdDoesNotQueryDslEvents() {
    DslEventRepository repository = mock(DslEventRepository.class);
    ExecutionSseService withRepo = new ExecutionSseService(new CbsSseProperties(500, 1000),
            providerOf(repository), new ObjectMapper());

    withRepo.subscribe("run-1", 0L);

    verify(repository, never()).findByAggregateSince(anyString(), anyString(), anyLong(), anyInt());
  }

  @Test
  void lastEventIdQueriesDslEventsForRunAndReplaysInOrder() throws Exception {
    DslEventRepository repository = mock(DslEventRepository.class);
    when(repository.findByAggregateSince(eq("run"), eq("run-1"), eq(10L), eq(1000)))
            .thenReturn(List.of(
                    row(11L, "RunStarted", Instant.parse("2024-01-01T00:00:00Z")),
                    row(12L, "RunCompleted", "COMPLETED", Instant.parse("2024-01-01T00:01:00Z"))));
    ExecutionSseService withRepo = new ExecutionSseService(new CbsSseProperties(500, 1000),
            providerOf(repository), new ObjectMapper());

    SseEmitter captured = mock(SseEmitter.class);
    withRepo.subscribe("run-1", 10L);
    replaceEmitter(withRepo, "run-1", captured);
    invokeBackfill(withRepo, "run-1", 10L);

    verify(repository, org.mockito.Mockito.atLeastOnce())
            .findByAggregateSince("run", "run-1", 10L, 1000);
    org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> sent = org.mockito.ArgumentCaptor
            .forClass(SseEmitter.SseEventBuilder.class);
    verify(captured, org.mockito.Mockito.times(2)).send(sent.capture());
    assertThat(extractId(sent.getAllValues().get(0))).isEqualTo("11");
    assertThat(extractId(sent.getAllValues().get(1))).isEqualTo("12");
  }

  @Test
  void backfillAdvancesLiveCursorPastReplayedEvents() throws Exception {
    DslEventRepository repository = mock(DslEventRepository.class);
    when(repository.findByAggregateSince(anyString(), anyString(), anyLong(), anyInt()))
            .thenReturn(List.of(row(20L, "RunCompleted", "COMPLETED", Instant.now())));
    ExecutionSseService withRepo = new ExecutionSseService(new CbsSseProperties(500, 1000),
            providerOf(repository), new ObjectMapper());
    SseEmitter captured = mock(SseEmitter.class);

    withRepo.subscribe("run-1", 19L);
    replaceEmitter(withRepo, "run-1", captured);
    invokeBackfill(withRepo, "run-1", 19L);
    withRepo.publish("run-1", "FAILED");

    org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> sent = org.mockito.ArgumentCaptor
            .forClass(SseEmitter.SseEventBuilder.class);
    verify(captured, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
    String liveId = extractId(sent.getAllValues().get(sent.getAllValues().size() - 1));
    // Live FAILED must use an id greater than the highest replayed id (20).
    assertThat(Long.parseLong(liveId)).isGreaterThan(20L);
  }

  @Test
  void backfillFromPayloadMapsRunStartedToRunning() throws Exception {
    DslEventRepository repository = mock(DslEventRepository.class);
    when(repository.findByAggregateSince(anyString(), anyString(), anyLong(), anyInt()))
            .thenReturn(List.of(row(50L, "RunStarted", Instant.now())));
    ExecutionSseService withRepo = new ExecutionSseService(new CbsSseProperties(500, 1000),
            providerOf(repository), new ObjectMapper());
    SseEmitter captured = mock(SseEmitter.class);

    withRepo.subscribe("run-1", 49L);
    replaceEmitter(withRepo, "run-1", captured);
    invokeBackfill(withRepo, "run-1", 49L);

    org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> sent = org.mockito.ArgumentCaptor
            .forClass(SseEmitter.SseEventBuilder.class);
    verify(captured).send(sent.capture());
    // The data entry is the ExecutionStatusChangedEvent; its status must be "Running"
    // (RunStarted has no status field, the backfill maps it to Running).
    Object data = extractData(sent.getValue());
    assertThat(data.toString()).contains("status=Running").contains("id=run-1");
  }

  private static Object extractData(SseEmitter.SseEventBuilder builder) throws Exception {
    for (Object dwt : builder.build()) {
      Field dataField = dwt.getClass().getDeclaredField("data");
      dataField.setAccessible(true);
      Object data = dataField.get(dwt);
      if (data != null && !(data instanceof String)) {
        return data;
      }
    }
    throw new IllegalStateException("no data entry found");
  }

  private static String extractId(SseEmitter.SseEventBuilder builder) throws Exception {
    // After send() is invoked, build() has been called and the SSE prefix lines (id/event/empty
    // data: header) live as a single String entry in the DataWithMediaType set.
    java.util.Set<?> built = builder.build();
    for (Object dwt : built) {
      Field dataField = dwt.getClass().getDeclaredField("data");
      dataField.setAccessible(true);
      Object data = dataField.get(dwt);
      if (data instanceof String s) {
        int idx = s.indexOf("id:");
        if (idx < 0) {
          continue;
        }
        int end = s.indexOf('\n', idx);
        String idLine = s.substring(idx, end);
        // "id:42" or "id: 42" — both are valid SSE shapes.
        return idLine.substring(idLine.indexOf(':') + 1).trim();
      }
    }
    throw new IllegalStateException("no id in built frame: " + built);
  }

  private static void invokeBackfill(ExecutionSseService svc, String id, long lastEventId)
          throws Exception {
    Field emittersField = ExecutionSseService.class.getDeclaredField("emitters");
    emittersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, ExecutionSseService.EmitterState> emitters = (Map<String, ExecutionSseService.EmitterState>) emittersField
            .get(svc);
    ExecutionSseService.EmitterState state = emitters.get(id);
    assertThat(state).isNotNull();
    java.lang.reflect.Method m = ExecutionSseService.class
            .getDeclaredMethod("backfillAndAdvance", String.class, long.class,
                    ExecutionSseService.EmitterState.class);
    m.setAccessible(true);
    m.invoke(svc, id, lastEventId, state);
  }

  @Test
  void backfillRespectsBackfillMax() {
    DslEventRepository repository = mock(DslEventRepository.class);
    when(repository.findByAggregateSince(anyString(), anyString(), anyLong(), anyInt()))
            .thenReturn(List.of());
    ExecutionSseService withRepo = new ExecutionSseService(new CbsSseProperties(500, 3),
            providerOf(repository), new ObjectMapper());

    withRepo.subscribe("run-1", 5L);

    verify(repository).findByAggregateSince("run", "run-1", 5L, 3);
  }

  @Test
  void emptyBackfillDoesNotBreakSubscribe() {
    DslEventRepository repository = mock(DslEventRepository.class);
    when(repository.findByAggregateSince(anyString(), anyString(), anyLong(), anyInt()))
            .thenReturn(List.of());
    ExecutionSseService withRepo = new ExecutionSseService(new CbsSseProperties(500, 1000),
            providerOf(repository), new ObjectMapper());

    SseEmitter emitter = withRepo.subscribe("run-1", 100L);

    assertThat(emitter).isNotNull();
    assertThat(withRepo.subscriberCount("run-1")).isEqualTo(1);
  }

  @Test
  void publishAttachesSequentialIdToLiveEvent() throws Exception {
    SseEmitter capture = mock(SseEmitter.class);
    service.subscribe("run-1", 0L);
    replaceEmitter(service, "run-1", capture);

    service.publish("run-1", "RUNNING");
    service.publish("run-1", "COMPLETED");

    org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> sent = org.mockito.ArgumentCaptor
            .forClass(SseEmitter.SseEventBuilder.class);
    verify(capture, org.mockito.Mockito.times(2)).send(sent.capture());
    // replaceEmitter installs a fresh cursor (1), so live publishes consume id 1 then id 2.
    assertThat(extractId(sent.getAllValues().get(0))).isEqualTo("1");
    assertThat(extractId(sent.getAllValues().get(1))).isEqualTo("2");
  }

  private static DslEventEntity row(long id, String eventType, Instant createdAt) {
    return row(id, eventType, null, createdAt);
  }

  private static DslEventEntity row(long id, String eventType, String status, Instant createdAt) {
    String payload = "{\"eventType\":\"" + eventType + "\","
            + (status == null ? "" : "\"status\":\"" + status + "\",")
            + "\"runId\":\"run-1\"}";
    return new DslEventEntity(id, eventType, "run", "run-1", null, payload, 1, createdAt);
  }

  private static <T> ObjectProvider<T> providerOf(T value) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> p = mock(ObjectProvider.class);
    when(p.getIfAvailable()).thenReturn(value);
    return p;
  }

  @SuppressWarnings("unchecked")
  private static void replaceEmitter(ExecutionSseService svc, String id, SseEmitter replacement)
          throws Exception {
    Field field = ExecutionSseService.class.getDeclaredField("emitters");
    field.setAccessible(true);
    Map<String, ExecutionSseService.EmitterState> emitters = (Map<String, ExecutionSseService.EmitterState>) field
            .get(svc);
    emitters.put(id, new ExecutionSseService.EmitterState(replacement,
            new java.util.concurrent.atomic.AtomicLong(1L)));
  }
}
