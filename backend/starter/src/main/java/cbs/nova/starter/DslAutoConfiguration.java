package cbs.nova.starter;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

@AutoConfiguration
public class DslAutoConfiguration {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  @PostConstruct
  void loadDslDefinitions() {
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return;
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException(
              "dsl.source-dir does not exist or is not a directory: " + dir);
    }
    DefinitionLoader.load(dir, GlobalManager.getInstance());
  }
}
