package cbs.nova.dsl.compact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelSourcePreprocessorTest {

  @Test
  void injectsTargetPackageWhenMissing() {
    var source = """
            public class OrderModel {
              public record Order(String id) {}
            }
            """;

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    assertThat(result.className()).isEqualTo("OrderModel");
    assertThat(result.preprocessedSource())
            .startsWith("package com.example.generated;")
            .contains("public class OrderModel");
  }

  @Test
  void overridesExistingPackageWithTargetPackage() {
    var source = """
            package com.example.models;

            public class OrderModel {
              public record Order(String id) {}
            }
            """;

    var result = ModelSourcePreprocessor.preprocess(
            "OrderModel.java", source, "com.example.generated");

    var output = result.preprocessedSource();
    assertThat(output)
            .startsWith("package com.example.generated;")
            .doesNotContain("package com.example.models;")
            .contains("public class OrderModel");
  }

  @Test
  void hoistsImportsWhenPackageIsMissing() {
    var source = """
            import java.util.List;

            public class OrderModel {
              private List<String> items;
            }
            """;

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    var output = result.preprocessedSource();
    assertThat(output).startsWith("package com.example.generated;");
    assertThat(output.indexOf("import java.util.List;"))
            .isLessThan(output.indexOf("public class OrderModel"));
    assertThat(output).contains("import java.util.List;");
  }

  @Test
  void hoistsImportsWhenPackageExists() {
    var source = """
            package com.example.models;

            import java.util.List;

            public class OrderModel {
              private List<String> items;
            }
            """;

    var result = ModelSourcePreprocessor.preprocess(
            "OrderModel.java", source, "com.example.generated");

    var output = result.preprocessedSource();
    assertThat(output).startsWith("package com.example.generated;");
    assertThat(output.indexOf("import java.util.List;"))
            .isLessThan(output.indexOf("public class OrderModel"));
    assertThat(output).contains("import java.util.List;");
  }

  @Test
  void preservesImportsWithoutDedup() {
    var source = """
            import java.util.List;
            import java.util.List;

            public class OrderModel {
            }
            """;

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    assertThat(result.preprocessedSource())
            .contains("import java.util.List;\nimport java.util.List;");
  }

  @Test
  void classNameIsFileNameMinusJavaExtension() {
    var source = "public class OrderModel {}";

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    assertThat(result.className()).isEqualTo("OrderModel");
  }

  @Test
  void fileNameWithoutJavaExtensionThrows() {
    assertThatThrownBy(() -> ModelSourcePreprocessor.preprocess(
            "OrderModel", "public class OrderModel {}", "com.example.generated"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must end with .java");
  }

  @Test
  void blankTargetPackageThrows() {
    assertThatThrownBy(() -> ModelSourcePreprocessor.preprocess(
            "OrderModel.java", "public class OrderModel {}", "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("targetPackage is required");
  }

  @Test
  void addsJsonAnnotationToMissingRecordsAndClasses() {
    var source = """
            public class OrderModel {
              public record Order(String id) {}
            }
            """;

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    assertThat(result.preprocessedSource())
            .contains("import io.avaje.jsonb.Json;")
            .contains("@Json\npublic class OrderModel")
            .contains("@Json\n  public record Order");
  }

  @Test
  void doesNotDuplicateJsonAnnotation() {
    var source = """
            import io.avaje.jsonb.Json;

            @Json
            public class OrderModel {
              @Json
              public record Order(String id) {}
            }
            """;

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    assertThat(result.preprocessedSource())
            .containsOnlyOnce("@Json\npublic class OrderModel")
            .containsOnlyOnce("@Json\n  public record Order");
  }

  @Test
  void doesNotAnnotateInterfacesOrEnums() {
    var source = """
            public interface Marker {}
            public enum Status { ACTIVE }
            public record Order(String id) {}
            """;

    var result = ModelSourcePreprocessor.preprocess("OrderModel.java", source,
            "com.example.generated");

    var output = result.preprocessedSource();
    assertThat(output)
            .contains("public interface Marker")
            .contains("public enum Status")
            .contains("@Json\npublic record Order")
            .doesNotContainPattern("@Json\n\s*public interface")
            .doesNotContainPattern("@Json\n\s*public enum");
  }
}
