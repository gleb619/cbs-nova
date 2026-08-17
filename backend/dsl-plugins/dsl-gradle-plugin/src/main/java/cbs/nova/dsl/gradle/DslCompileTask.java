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
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

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

  @Input
  @Optional
  public abstract Property<Boolean> getUseFileNameSubPackage();

  @Inject
  public abstract ExecOperations getExecOperations();

  public DslCompileTask() {
    getMainClass().set("cbs.nova.dsl.codegen.DslCompiler");
    getLogLevel().convention("INFO");
    getUseFileNameSubPackage().convention(true);
  }

  @Override
  public void exec() {
    var output = getOutputDir().get().getAsFile();
    output.mkdirs();

    var version = getBuildVersion().get();
    if (version.isBlank()) {
      version = resolveGitShortSha();
    }

    var properties = new Properties();
    properties.setProperty("srcDir", getSourceDir().get().getAsFile().getAbsolutePath());
    properties.setProperty("outputDir", output.getAbsolutePath());
    properties.setProperty("buildVersion", version);
    properties.setProperty("targetPackage", getDslPackage().getOrElse(""));
    properties.setProperty("logLevel", getLogLevel().getOrElse("INFO"));
    properties.setProperty("classpath", getClasspath().getAsPath());
    properties.setProperty("useFileNameSubPackage",
            Boolean.toString(getUseFileNameSubPackage().getOrElse(true)));

    setArgs(List.of(serializeProperties(properties)));

    var logLevel = getLogLevel().getOrElse("INFO");
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.toLowerCase());
    systemProperty(CompilerConstants.COMPILER_CLASSPATH_PROPERTY, getClasspath().getAsPath());
    super.exec();
  }

  private static String serializeProperties(Properties properties) {
    try (var writer = new StringWriter()) {
      properties.store(writer, null);
      return writer.toString();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize DSL compiler options", e);
    }
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
