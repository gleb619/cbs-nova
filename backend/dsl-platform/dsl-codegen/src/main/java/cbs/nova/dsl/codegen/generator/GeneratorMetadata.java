package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.Util.escapeJavaString;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GeneratorMetadata {

  static @NonNull String annotation(@NonNull Class<?> generatorClass) {
    String generator = generatorClass.getName();
    String timestamp = Instant.now().toString();
    String javaVersion = System.getProperty("java.version", "unknown");
    String user = System.getProperty("user.name", "unknown");
    String comments = "cbs-nova DSL codegen; java.version=%s, user.name=%s"
            .formatted(javaVersion, user);

    // TODO: we also need to add version of dsl platform itself, for later debug
    return """
            @DslGenerated(
                generator = "%s",
                timestamp = "%s",
                javaVersion = "%s",
                user = "%s")
            @Generated(
                value = "%s",
                date = "%s",
                comments = "%s")
            """.formatted(
            escapeJavaString(generator),
            escapeJavaString(timestamp),
            escapeJavaString(javaVersion),
            escapeJavaString(user),
            escapeJavaString(generator),
            escapeJavaString(timestamp),
            escapeJavaString(comments));
  }
}
