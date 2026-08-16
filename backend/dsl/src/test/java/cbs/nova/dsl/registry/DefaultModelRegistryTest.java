package cbs.nova.dsl.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

class DefaultModelRegistryTest {

  @TempDir
  Path tempDir;

  @Test
  void returnsEmptySetWhenNoProviders() {
    var registry = new DefaultModelRegistry();

    assertThat(registry.modelTypes()).isEmpty();
    assertThat(registry.isRegistered(String.class)).isFalse();
  }

  @Test
  void aggregatesProvidersFromClassLoader() throws Exception {
    var servicesDir = Files.createDirectories(tempDir.resolve("META-INF/services"));
    Files.writeString(servicesDir.resolve("cbs.nova.dsl.ModelRegistry"),
            "cbs.nova.dsl.registry.FakeModelRegistry");

    try (var classLoader = new URLClassLoader(
            new URL[]{tempDir.toUri().toURL()},
            getClass().getClassLoader())) {
      var registry = new DefaultModelRegistry(classLoader);

      assertThat(registry.modelTypes()).containsExactly(String.class);
      assertThat(registry.isRegistered(String.class)).isTrue();
      assertThat(registry.isRegistered(Integer.class)).isFalse();
    }
  }
}
