package cbs.nova.starter.preview;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.starter.ExternalCallTracker;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link TransactionInvoker} decorator that records each delegated transaction invocation as an
 * external {@value ExternalCallTracker#TYPE_ACTIVITY} call via {@link ExternalCallTracker}. This
 * lets preview/explain reports (and any other tracking context) see Temporal Activity-style
 * executions alongside HTTP and database calls.
 */
@RequiredArgsConstructor
public class TemporalActivityCallCaptureInterceptor implements TransactionInvoker {

  private final @NonNull TransactionInvoker delegate;
  private final @NonNull ExternalCallTracker externalCallTracker;

  @Override
  public @NonNull Result<?> invoke(@NonNull String name, @NonNull Object input,
          @NonNull Context<?> ctx) {
    var payload = new HashMap<String, Object>();
    payload.put("runId", ctx.runId());
    payload.put("mode", ctx.mode());
    payload.put("input", input);

    externalCallTracker.record(ExternalCallTracker.TYPE_ACTIVITY, name, "execute", payload);

    return delegate.invoke(name, input, ctx);
  }
}
