package cbs.nova.dsl.helper;

import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * SPI for exposing the originating source filename of generated helpers to the runtime registry.
 * Mirrors {@link cbs.nova.dsl.GeneratedClassProvider#filename()} for processes/transactions so
 * {@code GlobalManager.findFilename(name)} works uniformly across the catalog.
 */
public interface HelperSource {

  record Entry(@NonNull String name, @NonNull String filename) {

  }

  @NonNull
  List<Entry> entries();

}
