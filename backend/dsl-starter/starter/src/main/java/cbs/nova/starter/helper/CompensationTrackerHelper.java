package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.dsl.model.ExplainReport;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@Helper(name = "compensationTracker")
@RequiredArgsConstructor
public class CompensationTrackerHelper implements Executable<Map<String, Object>, String> {

  private final @NonNull Cache<String, Marker> markers;

  @Override
  public @NonNull Result<String> execute(@NonNull Context<Map<String, Object>> ctx) {
    Object marker = ctx.body().get("markerId");
    if (marker != null) {
      markers.put(marker.toString(), new Marker(marker.toString(), Instant.now()));
    }
    return Result.success("recorded");
  }

  @Override
  public @NonNull String description() {
    return """
            Write here short description about what helper used for
            """;
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull Context<Map<String, Object>> ctx) {
    return ExplainReport.builder()
            .name("compensationTracker")
            .description("""
                    Write here some text, use some var from a `ctx.body()`
                    """)
            .markdown("""
                    Add here some text and mermaid diagram
                    """)
            .build();
  }

  public boolean wasCompensated(String markerId) {
    return markerId != null && markers.getIfPresent(markerId) != null;
  }

  public void reset() {
    markers.invalidateAll();
  }

  public Map<String, Marker> markers() {
    markers.cleanUp();
    return Collections.unmodifiableMap(new HashMap<>(markers.asMap()));
  }

  public record Marker(String markerId, Instant createdAt) {
  }
}
