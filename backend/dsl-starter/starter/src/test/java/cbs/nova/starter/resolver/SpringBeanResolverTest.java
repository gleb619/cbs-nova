package cbs.nova.starter.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

class SpringBeanResolverTest {

  private ApplicationContext applicationContext;
  private SpringBeanResolver resolver;

  @BeforeEach
  void setUp() {
    applicationContext = mock(ApplicationContext.class);
    resolver = new SpringBeanResolver(applicationContext);
  }

  @Test
  void cachedResolveCallsApplicationContextOnce() {
    Object bean = new Object();
    when(applicationContext.getBean(Object.class)).thenReturn(bean);

    Object first = resolver.resolve(Object.class);
    Object second = resolver.resolve(Object.class);

    assertThat(first).isSameAs(bean);
    assertThat(second).isSameAs(bean);
    verify(applicationContext, times(1)).getBean(Object.class);
    verifyNoMoreInteractions(applicationContext);
  }

  @Test
  void unknownBeanThrowsIllegalStateExceptionAndIsNotCached() {
    when(applicationContext.getBean(Object.class))
            .thenThrow(new NoSuchBeanDefinitionException("no such bean"));

    assertThatThrownBy(() -> resolver.resolve(Object.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Bean not registered in Spring context: " + Object.class.getName())
            .hasCauseInstanceOf(NoSuchBeanDefinitionException.class);

    verify(applicationContext, times(1)).getBean(Object.class);

    assertThatThrownBy(() -> resolver.resolve(Object.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Bean not registered in Spring context: " + Object.class.getName())
            .hasCauseInstanceOf(NoSuchBeanDefinitionException.class);

    verify(applicationContext, times(2)).getBean(Object.class);
    verifyNoMoreInteractions(applicationContext);
  }
}
