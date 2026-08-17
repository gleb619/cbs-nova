package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

public interface DslDefinitionLoader {

  void load(@NonNull GlobalManager gm);

  void load(@NonNull Path sourceDir, @NonNull GlobalManager gm);

  void load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm);
}
