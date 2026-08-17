package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

public interface DslDefinitionLoader {

  int load(@NonNull GlobalManager gm);

  int load(@NonNull Path sourceDir, @NonNull GlobalManager gm);

  int load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm);
}
