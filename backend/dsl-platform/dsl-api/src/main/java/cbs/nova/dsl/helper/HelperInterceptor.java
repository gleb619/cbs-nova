package cbs.nova.dsl.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface HelperInterceptor {

  @NonNull
  Optional<Result<?>> intercept(@NonNull String helperName, @NonNull Context<?> ctx);
}
