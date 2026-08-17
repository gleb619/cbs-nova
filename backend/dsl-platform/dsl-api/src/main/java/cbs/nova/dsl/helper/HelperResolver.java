package cbs.nova.dsl.helper;

import org.jspecify.annotations.NonNull;

public interface HelperResolver {

  void registerHelpers(@NonNull HelperRegistrar registrar,
          @NonNull HelperInstanceResolver instanceResolver);

}
