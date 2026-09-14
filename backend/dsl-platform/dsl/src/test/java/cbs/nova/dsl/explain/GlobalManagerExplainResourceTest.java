package cbs.nova.dsl.explain;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalManagerExplainResourceTest {

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void registerExplainResourceMakesItDiscoverableByName() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("BatchProcessing", "sums", "batch-processing.md", "body"));

    assertThat(gm.describeExplainResource("BatchProcessing"))
            .get()
            .extracting(ExplainResource::content)
            .isEqualTo("body");
  }

  @Test
  void registerExplainResourceMakesItDiscoverableByFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("BatchProcessing", "sums", "batch-processing.md", "body"));

    assertThat(gm.describeExplainResourceByFilename("batch-processing.md"))
            .get()
            .extracting(ExplainResource::name)
            .isEqualTo("BatchProcessing");
  }

  @Test
  void unknownNameReturnsEmpty() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("A", "d", "a.md", ""));

    assertThat(gm.describeExplainResource("B")).isEmpty();
  }

  @Test
  void unknownFilenameReturnsEmpty() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("A", "d", "a.md", ""));

    assertThat(gm.describeExplainResourceByFilename("missing.md")).isEmpty();
  }

  @Test
  void registerExplainResourcesDiscoversServiceLoaderProviders() {
    var gm = GlobalManager.globalManager();
    // ServiceLoader discovery picks up META-INF/services entries from the test classpath.
    // The test module ships no provider, so we just verify the call is a no-op-friendly
    // initialiser (it does not throw and existing state is preserved).
    gm.registerExplainResources(GlobalManagerExplainResourceTest.class.getClassLoader());

    assertThat(gm.describeExplainResource("BatchProcessing")).isEmpty();
  }

  @Test
  void resolveExplainContentReturnsContentByName() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("Alpha", "d", "alpha.md", "alpha-body"));

    assertThat(gm.resolveExplainContent("Alpha")).isEqualTo("alpha-body");
  }

  @Test
  void resolveExplainContentReturnsContentByRawFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("Beta", "d", "beta.md", "beta-body"));

    assertThat(gm.resolveExplainContent("beta")).isEqualTo("beta-body");
  }

  @Test
  void resolveExplainContentReturnsContentByKebabCaseFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("BatchProcessing", "d", "batch-processing.md",
            "kebab-body"));

    assertThat(gm.resolveExplainContent("BatchProcessing")).isEqualTo("kebab-body");
  }

  @Test
  void resolveExplainContentPrefersNameOverFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(provider("Gamma", "by-name", "gamma.md", "name-body"));
    gm.registerExplainResource(provider("GammaFile", "by-file", "gammafile.md", "file-body"));

    assertThat(gm.resolveExplainContent("Gamma")).isEqualTo("name-body");
  }

  @Test
  void resolveExplainContentFallsBackToEmptyMarkdownWhenNothingMatches() {
    var gm = GlobalManager.globalManager();

    assertThat(gm.resolveExplainContent("Nothing"))
            .isEqualTo(cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN);
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
