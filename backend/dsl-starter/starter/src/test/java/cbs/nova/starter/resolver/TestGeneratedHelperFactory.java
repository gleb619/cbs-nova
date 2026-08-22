package cbs.nova.starter.resolver;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import org.jspecify.annotations.NonNull;

public final class TestGeneratedHelperFactory implements HelperInstanceResolver {

  @Override
  public @NonNull Executable<?, ?> resolve(@NonNull Class<?> helperClass) {
    if (helperClass == SpringOrGeneratedHelperInstanceResolverTest.ValidHelper.class) {
      return new SpringOrGeneratedHelperInstanceResolverTest.ValidHelper();
    }
    throw new IllegalStateException(
            "Helper is not registered by this generated factory: " + helperClass.getName());
  }
}