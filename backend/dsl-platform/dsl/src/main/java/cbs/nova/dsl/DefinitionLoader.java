package cbs.nova.dsl;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
public final class DefinitionLoader implements DslDefinitionLoader {

  private final ServiceLoaderDslDefinitionLoader serviceLoader = new ServiceLoaderDslDefinitionLoader();
  private final CompilingDslDefinitionLoader compiler = new CompilingDslDefinitionLoader();

  @Override
  public void load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    if (hasJavaSources(sourceDir)) {
      compiler.load(sourceDir, gm);
    } else {
      serviceLoader.load(gm);
    }
  }

  @Override
  public void load(@NonNull GlobalManager gm) {
    serviceLoader.load(gm);
  }

  @Override
  public void load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm) {
    serviceLoader.load(classLoader, gm);
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
