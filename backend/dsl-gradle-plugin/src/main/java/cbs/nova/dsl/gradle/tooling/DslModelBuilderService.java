package cbs.nova.dsl.gradle.tooling;

import cbs.nova.dsl.gradle.DslCompileExtension;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

public final class DslModelBuilderService implements ToolingModelBuilder {

  @Override
  public boolean canBuild(String modelName) {
    return modelName.equals(DslProjectModel.class.getName());
  }

  @Override
  public DslProjectModel buildAll(String modelName, Project project) {
    var extension = project.getExtensions().findByType(DslCompileExtension.class);
    if (extension == null) {
      return new DefaultDslProjectModel(
              project.file("src"), "dsl", "models", project.file("build/generated"), "");
    }
    return new DefaultDslProjectModel(
            extension.getSourceDir().getAsFile().get(),
            "dsl",
            "models",
            extension.getOutputDir().getAsFile().get(),
            extension.getDslPackage().getOrElse(""));
  }
}
