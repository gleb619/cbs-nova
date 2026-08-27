package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

//TODO: instead of `int` we need to return some `records` with drilldown details(like list another records and count for each type)
public interface DslDefinitionLoader {

  int load(@NonNull GlobalManager gm);

  int load(@NonNull Path sourceDir, @NonNull GlobalManager gm);

  int load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm);
}
