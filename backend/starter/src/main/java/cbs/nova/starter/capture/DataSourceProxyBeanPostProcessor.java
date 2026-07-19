package cbs.nova.starter.capture;

import cbs.nova.starter.ExternalCallTracker;
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
 * into the supplied {@link ExternalCallTracker}.
 */
public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

  private static final Set<Class<?>> DATA_SOURCE_INTERFACES = Set.of(
          DataSource.class,
          javax.sql.CommonDataSource.class,
          java.sql.Wrapper.class,
          AutoCloseable.class,
          Closeable.class);

  private final ExternalCallTracker externalCallTracker;

  public DataSourceProxyBeanPostProcessor(@NonNull ExternalCallTracker externalCallTracker) {
    this.externalCallTracker = externalCallTracker;
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
            dataSource, externalCallTracker);
    Class<?>[] interfaces = DATA_SOURCE_INTERFACES.toArray(new Class<?>[0]);
    return Proxy.newProxyInstance(classLoader, interfaces, handler);
  }
}
