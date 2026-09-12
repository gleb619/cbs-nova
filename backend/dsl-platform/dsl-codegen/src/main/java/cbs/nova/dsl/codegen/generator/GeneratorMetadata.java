package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.Util.escapeJavaString;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GeneratorMetadata {

  private static final String PLATFORM_VERSION_RESOURCE = "cbs-nova-dsl-codegen.properties";
  private static final String UNKNOWN = "unknown";
  private static final String UNEXPANDED_TOKEN = "${dslPlatformVersion}";

  private static final String PLATFORM_VERSION = loadPlatformVersion(
          GeneratorMetadata.class.getClassLoader());

  static @NonNull String annotation(@NonNull Class<?> generatorClass) {
    String generator = generatorClass.getName();
    String timestamp = Instant.now().toString();
    String javaVersion = System.getProperty("java.version", UNKNOWN);
    String user = System.getProperty("user.name", UNKNOWN);
    String comments = "cbs-nova DSL codegen; java.version=%s, user.name=%s, dsl.platform.version=%s"
            .formatted(javaVersion, user, PLATFORM_VERSION);

    return """
            @DslGenerated(
                generator = "%s",
                timestamp = "%s",
                javaVersion = "%s",
                user = "%s",
                dslPlatformVersion = "%s")
            @Generated(
                value = "%s",
                date = "%s",
                comments = "%s")
            """.formatted(
            escapeJavaString(generator),
            escapeJavaString(timestamp),
            escapeJavaString(javaVersion),
            escapeJavaString(user),
            escapeJavaString(PLATFORM_VERSION),
            escapeJavaString(generator),
            escapeJavaString(timestamp),
            escapeJavaString(comments));
  }

  static @NonNull String loadPlatformVersion(@NonNull ClassLoader classLoader) {
    try (InputStream in = classLoader.getResourceAsStream(PLATFORM_VERSION_RESOURCE)) {
      if (in == null) {
        return UNKNOWN;
      }
      var properties = new Properties();
      properties.load(in);
      String version = properties.getProperty("dslPlatformVersion");
      if (version == null || version.isBlank() || version.equals(UNEXPANDED_TOKEN)) {
        return UNKNOWN;
      }
      return version;
    } catch (IOException e) {
      return UNKNOWN;
    }
  }
}
