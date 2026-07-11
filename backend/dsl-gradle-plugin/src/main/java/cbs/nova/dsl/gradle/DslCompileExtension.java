package cbs.nova.dsl.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

public abstract class DslCompileExtension {

  public abstract DirectoryProperty getSourceDir();

  public abstract DirectoryProperty getOutputDir();

  public abstract Property<String> getDslVersion();

  public DslCompileExtension(Project project) {
    getSourceDir().convention(project.getLayout().getProjectDirectory().dir("src"));
    getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated"));
    getDslVersion().convention(project.getVersion().toString());
  }
}
