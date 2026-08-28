package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

public interface DslDefinitionLoader {

  LoadResult load(@NonNull GlobalManager gm);

  LoadResult load(@NonNull Path sourceDir, @NonNull GlobalManager gm);

  LoadResult load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm);
}
