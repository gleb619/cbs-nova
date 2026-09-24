package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.DslProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;

class DslSourcePathResolverTest {

  @TempDir
  Path sourceDir;

  @org.junit.jupiter.api.Test
  void returnsRelativePathForNestedFile() throws Exception {
    Path nested = sourceDir.resolve("dsl").resolve("LoanDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "step {}");

    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("LoanDsl.java"));

    assertThat(resolver.relativePath("LoanDisbursement"))
            .contains("dsl/LoanDsl.java");
  }

  @org.junit.jupiter.api.Test
  void returnsRelativePathForDeeplyNestedFile() throws Exception {
    Path nested = sourceDir.resolve("src").resolve("dsl").resolve("BatchDsl.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "step {}");

    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("src/dsl/BatchDsl.java"));

    assertThat(resolver.relativePath("BatchProcessing"))
            .contains("src/dsl/BatchDsl.java");
  }

  @org.junit.jupiter.api.Test
  void returnsRawFilenameWhenFileMissing() {
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("MissingDsl.java"));

    assertThat(resolver.relativePath("Unknown")).contains("MissingDsl.java");
  }

  @org.junit.jupiter.api.Test
  void returnsEmptyForUnknownDefinition() {
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.empty());

    assertThat(resolver.relativePath("Unknown")).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void returnsEmptyForBlankName() {
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("Foo.java"));

    assertThat(resolver.relativePath("")).isEmpty();
    assertThat(resolver.relativePath(null)).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void returnsRawFilenameWhenSourceDirUnset() {
    DslProperties props = DslProperties.builder().build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("FooDsl.java"));

    assertThat(resolver.relativePath("Foo")).contains("FooDsl.java");
  }

  @org.junit.jupiter.api.Test
  void returnsForwardSlashRelativePath() throws Exception {
    Path nested = sourceDir.resolve("dsl").resolve("Foo.java");
    Files.createDirectories(nested.getParent());
    Files.writeString(nested, "x");

    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver resolver = new DslSourcePathResolver(props,
            name -> Optional.of("dsl/Foo.java"));

    assertThat(resolver.relativePath("Foo")).contains("dsl/Foo.java");
  }
}
