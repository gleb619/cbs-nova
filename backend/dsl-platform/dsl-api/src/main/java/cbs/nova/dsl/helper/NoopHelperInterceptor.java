package cbs.nova.dsl.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public final class NoopHelperInterceptor implements HelperInterceptor {

  public static final NoopHelperInterceptor INSTANCE = new NoopHelperInterceptor();

  private NoopHelperInterceptor() {
  }

  @Override
  public @NonNull Optional<Result<?>> intercept(@NonNull String helperName,
          @NonNull Context<?> ctx) {
    return Optional.empty();
  }
}
