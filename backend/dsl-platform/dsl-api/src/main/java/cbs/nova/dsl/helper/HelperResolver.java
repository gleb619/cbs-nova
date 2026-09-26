package cbs.nova.dsl.helper;

import java.util.Optional;
import org.jspecify.annotations.NonNull;

public interface HelperResolver {

  void registerHelpers(@NonNull HelperRegistrar registrar,
          @NonNull HelperInstanceResolver instanceResolver);

  default @NonNull Optional<String> filename(@NonNull String name) {
    return Optional.empty();
  }
}
