package cbs.nova.dsl.gradle.tooling;

import java.io.File;
import java.io.Serializable;

public interface DslProjectModel extends Serializable {
  File getSourceDir();
  String getDslSubdir();
  String getModelsSubdir();
  File getOutputDir();
  String getDslPackage();
}
