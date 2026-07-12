package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Helper(name = "compensationTracker")
public class CompensationTrackerHelper implements Executable<Map<String, Object>, String> {

  private final Set<String> markers = ConcurrentHashMap.newKeySet();

  @Override
  public @NonNull Result<String> execute(@NonNull Context<Map<String, Object>> ctx) {
    Object marker = ctx.body().get("markerId");
    if (marker != null) {
      markers.add(marker.toString());
    }
    return Result.success("recorded");
  }

  public boolean wasCompensated(String markerId) {
    return markers.contains(markerId);
  }

  public void reset() {
    markers.clear();
  }

  public Set<String> markers() {
    return Collections.unmodifiableSet(markers);
  }
}
