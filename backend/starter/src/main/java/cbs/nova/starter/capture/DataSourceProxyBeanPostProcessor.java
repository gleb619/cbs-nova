package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.CommonDataSource;
import javax.sql.DataSource;

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.sql.Wrapper;
import java.util.Set;

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
    DataSourceInvocationHandler handler = new DataSourceInvocationHandler(
            dataSource, externalCallRecorder);
    Class<?>[] interfaces = DATA_SOURCE_INTERFACES.toArray(new Class<?>[0]);
    return Proxy.newProxyInstance(classLoader, interfaces, handler);
  }
}
