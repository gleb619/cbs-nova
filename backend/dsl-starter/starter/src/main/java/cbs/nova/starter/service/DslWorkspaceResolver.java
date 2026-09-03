package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import java.nio.file.Path;

public interface DslWorkspaceResolver {

  Path workspaceRoot();

  static DslWorkspaceResolver shared(DslProperties properties) {
    return () -> {
      String sourceDir = properties.getSourceDir();
      if (sourceDir == null || sourceDir.isBlank()) {
        throw new IllegalStateException("dsl.source-dir is not configured");
      }
      return Path.of(sourceDir).resolve(".workbench").resolve("drafts-fs").normalize();
    };
  }
}
