package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Helper(name = "compensationTracker")
public class CompensationTrackerHelper implements Executable<Map<String, Object>, String> {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
  private static final long CLEANUP_INTERVAL_SECONDS = 30;

  private final ConcurrentHashMap<String, Marker> markers = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler = Executors
          .newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "compensation-tracker-cleanup");
            t.setDaemon(true);
            return t;
          });

  public CompensationTrackerHelper() {
    cleanupScheduler.scheduleAtFixedRate(
            this::evictExpired, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS);
  }

  @Override
  public @NonNull Result<String> execute(@NonNull Context<Map<String, Object>> ctx) {
    Object marker = ctx.body().get("markerId");
    if (marker != null) {
      markers.putIfAbsent(marker.toString(), new Marker(marker.toString(), Instant.now()));
    }
    return Result.success("recorded");
  }

  public boolean wasCompensated(String markerId) {
    return markerId != null && markers.containsKey(markerId);
  }

  public void reset() {
    markers.clear();
    cleanupScheduler.shutdownNow();
  }

  public Map<String, Marker> markers() {
    return Collections.unmodifiableMap(markers);
  }

  private void evictExpired() {
    Instant cutoff = Instant.now().minus(DEFAULT_TTL);
    markers.values().removeIf(m -> m.createdAt().isBefore(cutoff));
  }

  public record Marker(String markerId, Instant createdAt) {
  }
}
