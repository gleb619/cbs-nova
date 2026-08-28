package cbs.nova.starter.preview;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.starter.core.StarterConstant;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;

@RequiredArgsConstructor
public class TemporalActivityCallCaptureInterceptor implements TransactionInvoker {

  private final @NonNull TransactionInvoker delegate;
  private final @NonNull ExternalCallRecorder externalCallRecorder;

  @Override
  public @NonNull Result<?> invoke(@NonNull String name, @NonNull Object input,
          @NonNull Context<?> ctx) {
    var payload = new HashMap<String, Object>();
    payload.put(StarterConstant.PAYLOAD_RUN_ID, ctx.runId());
    payload.put(StarterConstant.PAYLOAD_MODE, ctx.mode());
    payload.put(StarterConstant.PAYLOAD_INPUT, input);

    externalCallRecorder.record(ExternalCallRecorder.TYPE_ACTIVITY, name, "execute", payload);

    return delegate.invoke(name, input, ctx);
  }
}
