package cbs.nova.dsl.codegen.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class ModelImportResolverTest {

  private static final String BASE = "cbs.nova.dslexamples";
  private static final Set<String> MODELS = Set.of("BatchModels", "OrderModels");

  private final ModelImportResolver resolver = new ModelImportResolver(
          new CodegenNaming("cbs.nova.dsl.generated"));

  private static String source(String... imports) {
    return String.join("\n", imports) + "\n\nvoid main() {}\n";
  }

  @Test
  void matchesBaseStyleWildcardAndMember() {
    var found = resolver.extract(
            source(
                    "import cbs.nova.dslexamples.BatchModels.*;",
                    "import cbs.nova.dslexamples.BatchModels.BatchIn;"),
            BASE, "v1", MODELS);

    assertThat(found).hasSize(2);
    assertThat(found.get(0).modelClass()).isEqualTo("BatchModels");
    assertThat(found.get(0).member()).isEqualTo("*");
    assertThat(found.get(0).style()).isEqualTo(ModelImportResolver.ModelImport.Style.BASE);
    assertThat(found.get(1).member()).isEqualTo("BatchIn");
  }

  @Test
  void matchesBaseVersionStyle() {
    var found = resolver.extract(
            source("import cbs.nova.dslexamples.v1.BatchModels.BatchIn;"),
            BASE, "v1", MODELS);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).modelClass()).isEqualTo("BatchModels");
    assertThat(found.get(0).member()).isEqualTo("BatchIn");
    assertThat(found.get(0).style())
            .isEqualTo(ModelImportResolver.ModelImport.Style.BASE_VERSION);
  }

  @Test
  void matchesBaseVersionStyleWithNumericVersion() {
    var found = resolver.extract(
            source("import cbs.nova.dslexamples.v2.OrderModels.*;"),
            BASE, "2", MODELS);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).modelClass()).isEqualTo("OrderModels");
    assertThat(found.get(0).style())
            .isEqualTo(ModelImportResolver.ModelImport.Style.BASE_VERSION);
  }

  @Test
  void matchesVersionStyle() {
    var found = resolver.extract(
            source("import v1.BatchModels.BatchIn;"),
            BASE, "v1", MODELS);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).modelClass()).isEqualTo("BatchModels");
    assertThat(found.get(0).style()).isEqualTo(ModelImportResolver.ModelImport.Style.VERSION);
  }

  @Test
  void matchesBareStyle() {
    var found = resolver.extract(
            source("import BatchModels.*;"),
            BASE, "v1", MODELS);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).modelClass()).isEqualTo("BatchModels");
    assertThat(found.get(0).style()).isEqualTo(ModelImportResolver.ModelImport.Style.BARE);
  }

  @Test
  void noVersionStylesWhenVersionBlank() {
    var found = resolver.extract(
            source(
                    "import cbs.nova.dslexamples.v1.BatchModels.*;",
                    "import v1.BatchModels.*;"),
            BASE, " ", MODELS);

    assertThat(found).isEmpty();
  }

  @Test
  void bareAmbiguityFailsWithoutBasePackage() {
    assertThatThrownBy(() -> resolver.extract(
            source("import BatchModels.*;"),
            null, "v1", MODELS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BatchModels");
  }

  @Test
  void bareAllowedWithoutBasePackageWhenSingleModel() {
    var found = resolver.extract(
            source("import BatchModels.*;"),
            null, "v1", Set.of("BatchModels"));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).style()).isEqualTo(ModelImportResolver.ModelImport.Style.BARE);
  }

  @Test
  void versionSegmentCollidingWithModelClassResolvesAsBase() {
    var models = Set.of("v1", "BatchModels");
    var found = resolver.extract(
            source("import cbs.nova.dslexamples.v1.BatchModels.*;"),
            BASE, "v1", models);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).modelClass()).isEqualTo("v1");
    assertThat(found.get(0).member()).isEqualTo("BatchModels.*");
    assertThat(found.get(0).style()).isEqualTo(ModelImportResolver.ModelImport.Style.BASE);
  }

  @Test
  void unknownImportsAreIgnored() {
    var found = resolver.extract(
            source(
                    "import cbs.nova.dsl.Dsl;",
                    "import java.util.List;",
                    "import cbs.nova.dslexamples.Unknown.*;"),
            BASE, "v1", MODELS);

    assertThat(found).isEmpty();
  }

  @Test
  void rewriteCanonicalizesAllStyles() {
    var modelPackages = Map.of(
            "BatchModels", "cbs.nova.dslexamples.v1",
            "OrderModels", "cbs.nova.dslexamples.v1");
    var src = source(
            "import cbs.nova.dslexamples.BatchModels.*;",
            "import cbs.nova.dslexamples.v1.OrderModels.OrderIn;",
            "import v1.BatchModels.BatchIn;",
            "import BatchModels.*;",
            "import java.util.List;");

    var rewritten = resolver.rewrite(src, BASE, "v1", modelPackages, MODELS);

    assertThat(rewritten).contains("import cbs.nova.dslexamples.v1.BatchModels.*;");
    assertThat(rewritten).contains("import cbs.nova.dslexamples.v1.OrderModels.OrderIn;");
    assertThat(rewritten).contains("import cbs.nova.dslexamples.v1.BatchModels.BatchIn;");
    assertThat(rewritten).contains("import java.util.List;");
    assertThat(rewritten.lines().filter(l -> l.contains("import BatchModels"))).isEmpty();
  }

  @Test
  void rewriteLeavesUnmatchedImportsUntouched() {
    var src = source("import cbs.nova.dslexamples.Missing.*;");
    var rewritten = resolver.rewrite(src, BASE, "v1", Map.of(), MODELS);

    assertThat(rewritten).isEqualTo(src);
  }

  @Test
  void extractPreservesRawImport() {
    var found = resolver.extract(
            source("import cbs.nova.dslexamples.v1.BatchModels.*;"),
            BASE, "v1", MODELS);

    assertThat(found.get(0).rawImport()).isEqualTo("cbs.nova.dslexamples.v1.BatchModels.*");
  }
}
