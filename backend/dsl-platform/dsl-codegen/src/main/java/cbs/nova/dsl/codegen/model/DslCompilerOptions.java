package cbs.nova.dsl.codegen.model;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Properties;

@Slf4j
public record DslCompilerOptions(
        @NonNull Path srcDir,
        @NonNull Path outputDir,
        @NonNull String buildVersion,
        String targetPackage,
        @NonNull Level logLevel,
        String classpath,
        boolean useFileNameSubPackage) {

  private static final String DEFAULT_BUILD_VERSION = "v1";
  private static final String DEFAULT_LOG_LEVEL = "INFO";

  public static @NonNull DslCompilerOptions fromProperties(@NonNull String serialized) {
    var properties = new Properties();
    try (var reader = new StringReader(serialized)) {
      properties.load(reader);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse compiler options", e);
    }
    return fromProperties(properties);
  }

  public static @NonNull DslCompilerOptions fromProperties(@NonNull Properties properties) {
    var srcDir = requirePath(properties, "srcDir");
    var outputDir = requirePath(properties, "outputDir");
    var buildVersion = blankToNull(properties.getProperty("buildVersion"));
    var targetPackage = blankToNull(properties.getProperty("targetPackage"));
    var logLevel = parseLogLevel(properties.getProperty("logLevel"));
    var classpath = blankToNull(properties.getProperty("classpath"));
    var useFileNameSubPackage = parseBooleanFlag(properties.getProperty("useFileNameSubPackage"));

    return new DslCompilerOptions(
            srcDir,
            outputDir,
            buildVersion != null ? buildVersion : DEFAULT_BUILD_VERSION,
            targetPackage,
            logLevel,
            classpath,
            useFileNameSubPackage);
  }

  private static @NonNull Path requirePath(@NonNull Properties properties, @NonNull String key) {
    var value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      log.error("DSL_COMPILER_ERROR: Got next properties={}", properties);
      throw new IllegalArgumentException("Missing required compiler option: " + key);
    }
    return Path.of(value);
  }

  private static @NonNull Level parseLogLevel(String value) {
    var raw = blankToNull(value);
    if (raw == null) {
      return Level.valueOf(DEFAULT_LOG_LEVEL);
    }
    try {
      return Level.valueOf(raw.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid log level: " + raw, e);
    }
  }

  private static boolean parseBooleanFlag(String value) {
    var raw = blankToNull(value);
    return raw == null || Boolean.parseBoolean(raw);
  }

  private static String blankToNull(String value) {
    return value != null && !value.isBlank() ? value : null;
  }
}
