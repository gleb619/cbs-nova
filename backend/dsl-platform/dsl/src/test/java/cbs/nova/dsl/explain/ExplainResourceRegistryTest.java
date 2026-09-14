package cbs.nova.dsl.explain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainResourceRegistryTest {

  private ExplainResourceRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ExplainResourceRegistry();
  }

  @Test
  void registersAndFindsByName() {
    var provider = provider("BatchProcessing", "sums", "batch-processing.md", "body");
    registry.register(provider);

    assertThat(registry.findByName("BatchProcessing")).contains(provider);
    assertThat(registry.findByName("Missing")).isEmpty();
  }

  @Test
  void registersAndFindsByFilename() {
    var provider = provider("BatchProcessing", "sums", "batch-processing.md", "body");
    registry.register(provider);

    assertThat(registry.findByFilename("batch-processing.md")).contains(provider);
    assertThat(registry.findByFilename("missing.md")).isEmpty();
  }

  @Test
  void describeByNameReturnsSnapshot() {
    var provider = provider("BatchProcessing", "sums", "batch-processing.md", "body");
    registry.register(provider);

    var resource = registry.describeByName("BatchProcessing").orElseThrow();

    assertThat(resource.name()).isEqualTo("BatchProcessing");
    assertThat(resource.description()).isEqualTo("sums");
    assertThat(resource.filename()).isEqualTo("batch-processing.md");
    assertThat(resource.content()).isEqualTo("body");
  }

  @Test
  void describeByFilenameReturnsSnapshot() {
    var provider = provider("BatchProcessing", "sums", "batch-processing.md", "body");
    registry.register(provider);

    var resource = registry.describeByFilename("batch-processing.md").orElseThrow();

    assertThat(resource.name()).isEqualTo("BatchProcessing");
  }

  @Test
  void listsNamesAndFilenames() {
    registry.register(provider("A", "d", "a.md", ""));
    registry.register(provider("B", "d", "b.md", ""));

    assertThat(registry.names()).containsExactlyInAnyOrder("A", "B");
    assertThat(registry.filenames()).containsExactlyInAnyOrder("a.md", "b.md");
  }

  @Test
  void laterRegistrationOverridesEarlier() {
    var first = provider("A", "first", "a.md", "first body");
    var second = provider("A", "second", "a.md", "second body");
    registry.register(first);
    registry.register(second);

    assertThat(registry.findByName("A")).contains(second);
    assertThat(registry.findByFilename("a.md")).contains(second);
  }

  private static ExplainResourceProvider provider(
          String name, String description, String filename, String content) {
    return new ExplainResourceProvider() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public String filename() {
        return filename;
      }

      @Override
      public String content() {
        return content;
      }
    };
  }
}
