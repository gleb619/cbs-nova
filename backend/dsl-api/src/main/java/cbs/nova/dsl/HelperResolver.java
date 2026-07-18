package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface HelperResolver {

  void registerHelpers(@NonNull HelperRegistrar registrar, @NonNull HelperInstanceResolver instanceResolver);

}
