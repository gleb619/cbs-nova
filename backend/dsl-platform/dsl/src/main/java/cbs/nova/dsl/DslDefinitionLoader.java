package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface DslDefinitionLoader {

  LoadResult load(@NonNull GlobalManager gm);

  LoadResult load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm);
}
