package cbs.nova.dsl.utils;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.LoadResult;
import org.jspecify.annotations.NonNull;

public interface DslDefinitionLoader {

  LoadResult load(@NonNull GlobalManager gm);

  LoadResult load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm);
}
