package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import java.sql.Wrapper;
import javax.sql.CommonDataSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * BeanPostProcessor that transparently wraps every {@link DataSource} bean with a JDK dynamic
 * proxy. The proxy records every JDBC call (executeQuery / executeUpdate / execute / executeBatch)
 * into the supplied {@link ExternalCallRecorder}.
 */
//TODO: Usage of reflection is forbidden, add typed handler here
public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

  private static final Set<Class<?>> DATA_SOURCE_INTERFACES = Set.of(
          DataSource.class,
          CommonDataSource.class,
          Wrapper.class,
          AutoCloseable.class,
          Closeable.class);

  private final ExternalCallRecorder externalCallRecorder;

  public DataSourceProxyBeanPostProcessor(@NonNull ExternalCallRecorder externalCallRecorder) {
    this.externalCallRecorder = externalCallRecorder;
  }

  @Override
  public Object postProcessAfterInitialization(@NonNull Object bean, @Nullable String beanName) {
    if (!(bean instanceof DataSource dataSource)) {
      return bean;
    }
    ClassLoader classLoader = dataSource.getClass().getClassLoader();
    if (classLoader == null) {
      classLoader = DataSourceProxyBeanPostProcessor.class.getClassLoader();
    }
    ConnectionInvocationHandler handler = new ConnectionInvocationHandler(
            dataSource, externalCallRecorder);
    Class<?>[] interfaces = DATA_SOURCE_INTERFACES.toArray(new Class<?>[0]);
    return Proxy.newProxyInstance(classLoader, interfaces, handler);
  }
}
