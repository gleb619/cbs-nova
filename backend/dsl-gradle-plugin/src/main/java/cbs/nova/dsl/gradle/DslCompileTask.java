package cbs.nova.dsl.gradle;

import cbs.nova.dsl.codegen.CompilerConstants;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@CacheableTask
public abstract class DslCompileTask extends JavaExec {

  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract DirectoryProperty getSourceDir();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  @Input
  @Optional
  public abstract Property<String> getDslPackage();

  @Input
  @Optional
  public abstract Property<String> getBuildVersion();

  @Input
  @Optional
  public abstract Property<String> getLogLevel();

  @Input
  @Optional
  public abstract Property<String> getRuntimeModule();

  @Inject
  public abstract ExecOperations getExecOperations();

  public DslCompileTask() {
    getMainClass().set("cbs.nova.dsl.codegen.DslCompiler");
    getLogLevel().convention("TRACE");
  }

  @Override
  public void exec() {
    var output = getOutputDir().get().getAsFile();
    output.mkdirs();

    var args = new ArrayList<String>();
    args.add(getSourceDir().get().getAsFile().getAbsolutePath());
    args.add(output.getAbsolutePath());

    var version = getBuildVersion().get();
    if (version.isBlank()) {
      version = resolveGitShortSha();
    }
    if (!version.isBlank()) {
      args.add(version);
    }

    var dslPackage = getDslPackage().getOrElse("");
    if (!dslPackage.isBlank()) {
      if (version.isBlank()) {
        args.add("");
      }
      args.add(dslPackage);
    }

    var logLevel = getLogLevel().getOrElse("DEBUG");
    if (!logLevel.isBlank()) {
      if (version.isBlank()) {
        args.add("");
      }
      if (dslPackage.isBlank()) {
        args.add("");
      }
      args.add(logLevel);
      systemProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.toLowerCase());
    }

    setArgs(args);
    var classpath = getClasspath();
    systemProperty(CompilerConstants.COMPILER_CLASSPATH_PROPERTY, classpath.getAsPath());
    super.exec();
  }

  private String resolveGitShortSha() {
    try {
      var stdout = new ByteArrayOutputStream();
      getExecOperations().exec(spec -> {
        spec.commandLine("git", "rev-parse", "--short", "HEAD");
        spec.setStandardOutput(stdout);
        spec.setErrorOutput(new ByteArrayOutputStream());
      });
      var sha = stdout.toString(StandardCharsets.UTF_8).trim();
      return sha.isEmpty() ? "unknown" : sha;
    } catch (Exception e) {
      getLogger().warn("Failed to resolve git short SHA; using 'unknown': {}", e.getMessage());
      return "unknown";
    }
  }
}
