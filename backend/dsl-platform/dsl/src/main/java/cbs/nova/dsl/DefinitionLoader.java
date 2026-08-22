package cbs.nova.dsl;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
// TODO: @deprecated since T230, use `ServiceLoaderDslDefinitionLoader` instead
@Deprecated(forRemoval = true)
public final class DefinitionLoader implements DslDefinitionLoader {

  private final ServiceLoaderDslDefinitionLoader serviceLoader = new ServiceLoaderDslDefinitionLoader();
  private final CompilingDslDefinitionLoader compiler = new CompilingDslDefinitionLoader(
          new ServiceLoaderDslDefinitionLoader());

  @Override
  public int load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    int result;
    if (hasJavaSources(sourceDir)) {
      result = compiler.load(sourceDir, gm);
    } else {
      result = serviceLoader.load(gm);
    }

    return result;
  }

  @Override
  public int load(@NonNull GlobalManager gm) {
    return serviceLoader.load(gm);
  }

  @Override
  public int load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm) {
    return serviceLoader.load(classLoader, gm);
  }

  private boolean hasJavaSources(@NonNull Path sourceDir) {
    try (var stream = Files.walk(sourceDir)) {
      return stream.anyMatch(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      log.warn("[DefinitionLoader] Failed to scan {}: {}", sourceDir, e.getMessage());
      return false;
    }
  }
}
