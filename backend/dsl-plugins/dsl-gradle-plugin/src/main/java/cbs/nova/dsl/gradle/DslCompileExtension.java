package cbs.nova.dsl.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

public abstract class DslCompileExtension {

  public abstract DirectoryProperty getSourceDir();

  public abstract DirectoryProperty getOutputDir();

  public abstract Property<String> getDslVersion();

  public abstract Property<String> getDslPackage();

  public abstract Property<String> getBuildVersion();

  public abstract Property<String> getLogLevel();

  public abstract Property<String> getRuntimeModule();

  public abstract Property<Boolean> getUseFileNameSubPackage();

  public DslCompileExtension(Project project) {
    getSourceDir().convention(project.getLayout().getProjectDirectory().dir("src"));
    getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated"));
    getDslVersion().convention(project.provider(() -> project.getVersion().toString()));
    getDslPackage().convention("");
    getBuildVersion().convention("");
    getLogLevel().convention("INFO");
    getRuntimeModule().convention("starter");
    getUseFileNameSubPackage().convention(false);
  }
}
