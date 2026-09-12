package cbs.nova.dsl.codegen.generator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeneratorMetadataTest {

  @Test
  void emittedAnnotationContainsDslPlatformVersion() {
    String annotation = GeneratorMetadata.annotation(GeneratorMetadataTest.class);

    assertThat(annotation).contains("dslPlatformVersion = \"");
  }

  @Test
  void emittedGeneratedCommentsContainDslPlatformVersion() {
    String annotation = GeneratorMetadata.annotation(GeneratorMetadataTest.class);

    assertThat(annotation).contains("dsl.platform.version=");
  }

  @Test
  void platformVersionResolvesFromFilteredResourceOnClasspath() {
    String version = GeneratorMetadata
            .loadPlatformVersion(GeneratorMetadataTest.class.getClassLoader());

    assertThat(version).isNotEqualTo("unknown");
    assertThat(version).doesNotContain("${");
  }

  @Test
  void platformVersionFallsBackToUnknownWhenResourceIsMissing() {
    // A classloader with no parent cannot delegate to the app classpath, so the
    // properties file is missing — the same state as running off raw unfiltered output.
    var emptyClassLoader = new ClassLoader(null) {
    };

    String version = GeneratorMetadata.loadPlatformVersion(emptyClassLoader);

    assertThat(version).isEqualTo("unknown");
  }

  @Test
  void platformVersionFallsBackToUnknownWhenTokenIsUnexpanded() throws Exception {
    var rawUnfiltered = new ClassLoader(GeneratorMetadataTest.class.getClassLoader()) {
      @Override
      public java.io.InputStream getResourceAsStream(String name) {
        if ("cbs-nova-dsl-codegen.properties".equals(name)) {
          return new java.io.ByteArrayInputStream(
                  "dslPlatformVersion=${dslPlatformVersion}\n"
                          .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return super.getResourceAsStream(name);
      }
    };

    String version = GeneratorMetadata.loadPlatformVersion(rawUnfiltered);

    assertThat(version).isEqualTo("unknown");
  }
}
