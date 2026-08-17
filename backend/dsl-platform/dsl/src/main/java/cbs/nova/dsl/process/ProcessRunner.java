package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

public interface ProcessRunner {

  @NonNull
  Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx);
}
