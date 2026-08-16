package cbs.nova.dsl.gradle.tooling;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public final class DefaultDslProjectModel implements DslProjectModel, Serializable {

  private final File sourceDir;
  private final String dslSubdir;
  private final String modelsSubdir;
  private final File outputDir;
  private final String dslPackage;

}
