package cbs.nova.dsl.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.util.List;

@CacheableTask
public abstract class DslCompileTask extends JavaExec {

  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract DirectoryProperty getSourceDir();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  public DslCompileTask() {
    getMainClass().set("cbs.nova.dsl.codegen.DslCompiler");
  }

  @Override
  public void exec() {
    var output = getOutputDir().get().getAsFile();
    output.mkdirs();
    setArgs(List.of(getSourceDir().get().getAsFile().getAbsolutePath(), output.getAbsolutePath()));
    super.exec();
  }
}
