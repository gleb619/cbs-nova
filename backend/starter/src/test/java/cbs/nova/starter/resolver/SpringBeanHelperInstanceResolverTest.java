package cbs.nova.starter.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

class SpringBeanHelperInstanceResolverTest {

  private ApplicationContext applicationContext;
  private SpringBeanHelperInstanceResolver resolver;

  @BeforeEach
  void setUp() {
    applicationContext = mock(ApplicationContext.class);
    resolver = new SpringBeanHelperInstanceResolver(applicationContext);
    ValidHelper.CONSTRUCTION_COUNT.set(0);
  }

  @Test
  void beanPresentReturnsBeanAndDoesNotReflect() {
    ValidHelper bean = new ValidHelper();
    ValidHelper.CONSTRUCTION_COUNT.set(0);
    when(applicationContext.getBean(ValidHelper.class)).thenReturn(bean);

    Executable<?, ?> resolved = resolver.resolve(ValidHelper.class);

    assertThat(resolved).isSameAs(bean);
    assertThat(ValidHelper.CONSTRUCTION_COUNT).hasValue(0);
    verify(applicationContext, times(1)).getBean(ValidHelper.class);
    verifyNoMoreInteractions(applicationContext);
  }

  @Test
  void beanAbsentCreatesFreshInstanceViaReflection() {
    when(applicationContext.getBean(ValidHelper.class))
            .thenThrow(new NoSuchBeanDefinitionException("no such bean"));

    Executable<?, ?> first = resolver.resolve(ValidHelper.class);
    Executable<?, ?> second = resolver.resolve(ValidHelper.class);

    assertThat(first).isInstanceOf(ValidHelper.class).isNotSameAs(second);
    assertThat(ValidHelper.CONSTRUCTION_COUNT).hasValue(2);
    verify(applicationContext, times(2)).getBean(ValidHelper.class);
    verifyNoMoreInteractions(applicationContext);
  }

  @Test
  void missingNoArgConstructorThrowsIllegalStateExceptionWithCause() {
    when(applicationContext.getBean(NoNoArgHelper.class))
            .thenThrow(new NoSuchBeanDefinitionException("no such bean"));

    assertThatThrownBy(() -> resolver.resolve(NoNoArgHelper.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                    "Helper class must declare a public no-arg constructor: "
                            + NoNoArgHelper.class.getName())
            .hasCauseInstanceOf(NoSuchMethodException.class);

    verify(applicationContext, times(1)).getBean(NoNoArgHelper.class);
    verifyNoMoreInteractions(applicationContext);
  }

  @Test
  void throwingConstructorThrowsIllegalStateException() {
    when(applicationContext.getBean(ThrowingHelper.class))
            .thenThrow(new NoSuchBeanDefinitionException("no such bean"));

    assertThatThrownBy(() -> resolver.resolve(ThrowingHelper.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to create helper instance: " + ThrowingHelper.class.getName())
            .hasCauseInstanceOf(ReflectiveOperationException.class);

    verify(applicationContext, times(1)).getBean(ThrowingHelper.class);
    verifyNoMoreInteractions(applicationContext);
  }

  static class ValidHelper implements Executable<Object, Object> {
    static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();

    ValidHelper() {
      CONSTRUCTION_COUNT.incrementAndGet();
    }

    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("ok");
    }
  }

  static class NoNoArgHelper implements Executable<Object, Object> {
    private final String value;

    NoNoArgHelper(String value) {
      this.value = value;
    }

    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success(value);
    }
  }

  static class ThrowingHelper implements Executable<Object, Object> {
    ThrowingHelper() {
      throw new RuntimeException("ctor failure");
    }

    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("never");
    }
  }
}
