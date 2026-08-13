package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Typed {@link InvocationHandler} installed by {@link DataSourceProxyBeanPostProcessor} for every
 * proxied {@link DataSource}. Delegates JDBC interface methods to
 * {@link DataSourceMethodDispatcher} and defines proxy identity / string representation for
 * {@code Object} methods.
 */
public class DataSourceInvocationHandler implements InvocationHandler {

  private final DataSource dataSource;
  private final DataSourceMethodDispatcher dispatcher;

  public DataSourceInvocationHandler(@NonNull DataSource dataSource,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this.dataSource = dataSource;
    this.dispatcher = new DataSourceMethodDispatcher(dataSource, externalCallRecorder);
  }

  @Override
  public Object invoke(@NonNull Object proxy, @NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    return switch (method.getName()) {
      case "equals" -> proxy == args[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "DataSourceProxy[" + dataSource + "]";
      default -> dispatcher.dispatch(method, args);
    };
  }
}
