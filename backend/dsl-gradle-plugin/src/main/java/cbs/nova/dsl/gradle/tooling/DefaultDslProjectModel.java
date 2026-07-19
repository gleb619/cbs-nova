package cbs.nova.dsl.gradle.tooling;

import java.io.File;
import java.io.Serializable;

public final class DefaultDslProjectModel implements DslProjectModel, Serializable {

  private final File sourceDir;
  private final String dslSubdir;
  private final String modelsSubdir;
  private final File outputDir;
  private final String dslPackage;

  public DefaultDslProjectModel(
          File sourceDir, String dslSubdir, String modelsSubdir, File outputDir, String dslPackage) {
    this.sourceDir = sourceDir;
    this.dslSubdir = dslSubdir;
    this.modelsSubdir = modelsSubdir;
    this.outputDir = outputDir;
    this.dslPackage = dslPackage;
  }

  @Override
  public File getSourceDir() {
    return sourceDir;
  }

  @Override
  public String getDslSubdir() {
    return dslSubdir;
  }

  @Override
  public String getModelsSubdir() {
    return modelsSubdir;
  }

  @Override
  public File getOutputDir() {
    return outputDir;
  }

  @Override
  public String getDslPackage() {
    return dslPackage;
  }
}
