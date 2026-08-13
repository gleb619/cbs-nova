package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;

/**
 * Typed {@link InvocationHandler} for a proxied {@link Connection}. Statement factory methods are
 * routed to {@link ConnectionMethodDispatcher}, which wraps returned statements so their executions
 * can be recorded. All other {@link Connection} methods are forwarded via typed calls.
 */
public class ConnectionInvocationHandler implements InvocationHandler {

  public static final String FALLBACK_TARGET = "jdbc:datasource";

  private final Connection connection;
  private final ConnectionMethodDispatcher dispatcher;

  public ConnectionInvocationHandler(@NonNull Connection connection,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this.connection = connection;
    this.dispatcher = new ConnectionMethodDispatcher(connection, externalCallRecorder);
  }

  @Override
  public Object invoke(@NonNull Object proxy, @NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    return switch (method.getName()) {
      case "equals" -> proxy == args[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "ConnectionProxy[" + connection + "]";
      default -> dispatcher.dispatch(method, args);
    };
  }
}
