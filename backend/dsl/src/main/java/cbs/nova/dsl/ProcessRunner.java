package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface ProcessRunner {
  @NonNull
  Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx);
}
