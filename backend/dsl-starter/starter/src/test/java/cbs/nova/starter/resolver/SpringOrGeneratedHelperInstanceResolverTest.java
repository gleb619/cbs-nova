package cbs.nova.starter.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

class SpringOrGeneratedHelperInstanceResolverTest {

  @Test
  void springReturnsBeanIsReturnedAndGeneratedFactoriesNotConsulted() {
    HelperInstanceResolver springResolver = mock(HelperInstanceResolver.class);
    HelperInstanceResolver generated = mock(HelperInstanceResolver.class);
    var resolver = new SpringOrGeneratedHelperInstanceResolver(springResolver, List.of(generated));

    ValidHelper bean = new ValidHelper();
    doReturn(bean).when(springResolver).resolve(ValidHelper.class);

    Executable<?, ?> resolved = resolver.resolve(ValidHelper.class);

    assertThat(resolved).isSameAs(bean);
    verify(springResolver, times(1)).resolve(ValidHelper.class);
    verify(generated, never()).resolve(ValidHelper.class);
  }

  @Test
  void springThrowsNoSuchBeanFallsBackToGeneratedFactory() {
    HelperInstanceResolver springResolver = mock(HelperInstanceResolver.class);
    HelperInstanceResolver generated = mock(HelperInstanceResolver.class);
    var resolver = new SpringOrGeneratedHelperInstanceResolver(springResolver, List.of(generated));

    NoSuchBeanDefinitionException nsb = new NoSuchBeanDefinitionException("no such bean");
    IllegalStateException wrapped = new IllegalStateException("wrapped", nsb);
    when(springResolver.resolve(GeneratedHelper.class)).thenThrow(wrapped);
    GeneratedHelper generatedInstance = new GeneratedHelper();
    doReturn(generatedInstance).when(generated).resolve(GeneratedHelper.class);

    Executable<?, ?> resolved = resolver.resolve(GeneratedHelper.class);

    assertThat(resolved).isSameAs(generatedInstance);
    verify(springResolver, times(1)).resolve(GeneratedHelper.class);
    verify(generated, times(1)).resolve(GeneratedHelper.class);
  }

  @Test
  void springThrowsNoSuchBeanAndNoGeneratedFactoryMatchesThrowsIllegalState() {
    HelperInstanceResolver springResolver = mock(HelperInstanceResolver.class);
    HelperInstanceResolver generated = mock(HelperInstanceResolver.class);
    var resolver = new SpringOrGeneratedHelperInstanceResolver(springResolver, List.of(generated));

    NoSuchBeanDefinitionException nsb = new NoSuchBeanDefinitionException("no such bean");
    IllegalStateException wrapped = new IllegalStateException("wrapped", nsb);
    when(springResolver.resolve(UnknownHelper.class)).thenThrow(wrapped);
    when(generated.resolve(UnknownHelper.class))
            .thenThrow(new IllegalStateException("not this factory"));

    assertThatThrownBy(() -> resolver.resolve(UnknownHelper.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(UnknownHelper.class.getName())
            .hasMessageContaining("not a Spring bean")
            .hasCause(wrapped);

    verify(springResolver, times(1)).resolve(UnknownHelper.class);
    verify(generated, times(1)).resolve(UnknownHelper.class);
  }

  @Test
  void emptyGeneratedListAndSpringThrowsNoSuchBeanThrowsIllegalState() {
    HelperInstanceResolver springResolver = mock(HelperInstanceResolver.class);
    var resolver = new SpringOrGeneratedHelperInstanceResolver(springResolver, List.of());

    NoSuchBeanDefinitionException nsb = new NoSuchBeanDefinitionException("no such bean");
    IllegalStateException wrapped = new IllegalStateException("wrapped", nsb);
    when(springResolver.resolve(UnknownHelper.class)).thenThrow(wrapped);

    assertThatThrownBy(() -> resolver.resolve(UnknownHelper.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(UnknownHelper.class.getName())
            .hasCause(wrapped);

    verify(springResolver, times(1)).resolve(UnknownHelper.class);
  }

  @Test
  void springThrowsIllegalStateWithoutNoSuchBeanCauseRethrowsSameException() {
    HelperInstanceResolver springResolver = mock(HelperInstanceResolver.class);
    HelperInstanceResolver generated = mock(HelperInstanceResolver.class);
    var resolver = new SpringOrGeneratedHelperInstanceResolver(springResolver, List.of(generated));

    IllegalStateException unrelated = new IllegalStateException("boom");
    when(springResolver.resolve(ValidHelper.class)).thenThrow(unrelated);

    assertThatThrownBy(() -> resolver.resolve(ValidHelper.class)).isSameAs(unrelated);

    verify(springResolver, times(1)).resolve(ValidHelper.class);
    verify(generated, never()).resolve(ValidHelper.class);
  }

  static class ValidHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("ok");
    }
  }

  static class GeneratedHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("generated");
    }
  }

  static class UnknownHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("ok");
    }
  }
}
