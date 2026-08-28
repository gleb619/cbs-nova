package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;

@Helper(name = "compensationTracker")
public class CompensationTrackerHelper implements Executable<Map<String, Object>, String> {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
  private static final long DEFAULT_MAX_SIZE = 10_000L;

  private final Cache<String, Marker> markers;

  public CompensationTrackerHelper() {
    this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
  }

  CompensationTrackerHelper(Duration ttl, long maxSize) {
    this.markers = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build();
  }

  @Override
  public @NonNull Result<String> execute(@NonNull Context<Map<String, Object>> ctx) {
    Object marker = ctx.body().get("markerId");
    if (marker != null) {
      markers.put(marker.toString(), new Marker(marker.toString(), Instant.now()));
    }
    return Result.success("recorded");
  }

  public boolean wasCompensated(String markerId) {
    return markerId != null && markers.getIfPresent(markerId) != null;
  }

  public void reset() {
    markers.invalidateAll();
  }

  public Map<String, Marker> markers() {
    return Collections.unmodifiableMap(new HashMap<>(markers.asMap()));
  }

  public record Marker(String markerId, Instant createdAt) {
  }
}
