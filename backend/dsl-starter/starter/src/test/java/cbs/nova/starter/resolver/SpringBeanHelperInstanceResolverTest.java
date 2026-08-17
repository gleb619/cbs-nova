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

class SpringBeanHelperInstanceResolverTest {

  private ApplicationContext applicationContext;
  private SpringBeanHelperInstanceResolver resolver;

  @BeforeEach
  void setUp() {
    applicationContext = mock(ApplicationContext.class);
    resolver = new SpringBeanHelperInstanceResolver(applicationContext);
  }

  @Test
  void beanPresentReturnsBeanAndDoesNotReflect() {
    ValidHelper bean = new ValidHelper();
    when(applicationContext.getBean(ValidHelper.class)).thenReturn(bean);

    Executable<?, ?> resolved = resolver.resolve(ValidHelper.class);

    assertThat(resolved).isSameAs(bean);
    verify(applicationContext, times(1)).getBean(ValidHelper.class);
    verifyNoMoreInteractions(applicationContext);
  }

  @Test
  void beanAbsentThrowsIllegalStateExceptionWithCause() {
    when(applicationContext.getBean(ValidHelper.class))
            .thenThrow(new NoSuchBeanDefinitionException("no such bean"));

    assertThatThrownBy(() -> resolver.resolve(ValidHelper.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Helper is not registered as a Spring bean: " + ValidHelper.class.getName())
            .hasCauseInstanceOf(NoSuchBeanDefinitionException.class);

    verify(applicationContext, times(1)).getBean(ValidHelper.class);
    verifyNoMoreInteractions(applicationContext);
  }

  static class ValidHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("ok");
    }
  }
}
