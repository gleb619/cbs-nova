package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class PreparedStatementInvocationHandler implements InvocationHandler {

  private final Object delegate;
  private final StatementMethodDispatcher dispatcher;

  public PreparedStatementInvocationHandler(@NonNull Object delegate, @Nullable String sql,
          @NonNull String target, @NonNull ExternalCallRecorder externalCallRecorder) {
    this.delegate = delegate;
    this.dispatcher = new StatementMethodDispatcher(delegate, sql, target, externalCallRecorder);
  }

  @Override
  public Object invoke(@NonNull Object proxy, @NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    return switch (method.getName()) {
      case "equals" -> proxy == args[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "StatementProxy[" + delegate + "]";
      default -> dispatcher.dispatch(method, args);
    };
  }
}
