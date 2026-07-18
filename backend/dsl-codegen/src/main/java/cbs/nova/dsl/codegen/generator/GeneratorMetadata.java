package cbs.nova.dsl.codegen.generator;

import cbs.nova.dsl.codegen.util.EscapeUtil;
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
            EscapeUtil.escapeJavaString(generator),
            EscapeUtil.escapeJavaString(timestamp),
            EscapeUtil.escapeJavaString(javaVersion),
            EscapeUtil.escapeJavaString(user),
            EscapeUtil.escapeJavaString(generator),
            EscapeUtil.escapeJavaString(timestamp),
            EscapeUtil.escapeJavaString(comments));
  }
}
