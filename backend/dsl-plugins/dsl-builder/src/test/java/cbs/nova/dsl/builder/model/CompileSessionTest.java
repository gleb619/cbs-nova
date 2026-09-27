package cbs.nova.dsl.builder.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompileSessionTest {

  @TempDir
  Path tempDir;

  @Test
  void isExpiredIsFalseWhenNowIsBeforeCreatedAtPlusTtl() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    CompileSession session = session(createdAt, tempDir);
    Duration ttl = Duration.ofMinutes(10);

    boolean expired = session.isExpired(ttl, createdAt.plus(Duration.ofMinutes(5)));

    assertThat(expired).isFalse();
  }

  @Test
  void isExpiredIsFalseAtExactBoundaryInstant() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    CompileSession session = session(createdAt, tempDir);
    Duration ttl = Duration.ofMinutes(10);
    Instant boundary = createdAt.plus(ttl);

    // isBefore() is strict: the boundary instant itself is NOT expired.
    boolean expired = session.isExpired(ttl, boundary);

    assertThat(expired).isFalse();
  }

  @Test
  void isExpiredIsTrueWhenNowIsWellPastCreatedAtPlusTtl() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    CompileSession session = session(createdAt, tempDir);
    Duration ttl = Duration.ofMinutes(10);

    boolean expired = session.isExpired(ttl, createdAt.plus(Duration.ofMinutes(11)));

    assertThat(expired).isTrue();
  }

  @Test
  void srcDirResolvesToProjectDirSrc() {
    CompileSession session = session(Instant.now(), tempDir);

    assertThat(session.srcDir()).isEqualTo(tempDir.resolve("src"));
  }

  @Test
  void writeZipWritesRelativeEntriesFromClassesAndResourcesDirsWithRoundTrippedContent()
          throws IOException {
    Path classesDir = tempDir.resolve("build").resolve("classes").resolve("java").resolve("main");
    Path resourcesDir = tempDir.resolve("build").resolve("resources").resolve("main");
    Files.createDirectories(classesDir.resolve("cbs/nova/example"));
    Files.createDirectories(resourcesDir.resolve("META-INF/services"));

    Files.writeString(classesDir.resolve("cbs/nova/example/Foo.class"), "class-bytes",
            StandardCharsets.UTF_8);
    Files.writeString(resourcesDir.resolve("META-INF/services/cbs.nova.dsl.SomeProvider"),
            "cbs.nova.example.FooProvider", StandardCharsets.UTF_8);

    CompileSession session = session(Instant.now(), tempDir);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    session.writeZip(out);

    Map<String, String> entries = readZip(out.toByteArray());
    assertThat(entries).containsOnlyKeys(
            "cbs/nova/example/Foo.class",
            "META-INF/services/cbs.nova.dsl.SomeProvider");
    assertThat(entries.get("cbs/nova/example/Foo.class")).isEqualTo("class-bytes");
    assertThat(entries.get("META-INF/services/cbs.nova.dsl.SomeProvider"))
            .isEqualTo("cbs.nova.example.FooProvider");
    // No path uses the platform separator or a leading directory prefix.
    assertThat(entries.keySet()).allSatisfy(name -> assertThat(name).doesNotContain("\\"));
  }

  @Test
  void writeZipOnMissingBuildOutputProducesAnEmptyValidZip() throws IOException {
    // Neither build/classes/java/main nor build/resources/main exists under projectDir.
    CompileSession session = session(Instant.now(), tempDir);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    session.writeZip(out);

    Map<String, String> entries = readZip(out.toByteArray());
    assertThat(entries).isEmpty();
  }

  private static CompileSession session(Instant createdAt, Path projectDir) {
    return new CompileSession("session-1", projectDir.resolve("session"), projectDir,
            projectDir.resolve("out"), createdAt);
  }

  private static Map<String, String> readZip(byte[] zipBytes) throws IOException {
    Map<String, String> entries = new HashMap<>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return entries;
  }
}
