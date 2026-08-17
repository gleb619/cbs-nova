package cbs.nova.dsl.codegen.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTypeExtractorTest {

  private final ModelTypeExtractor extractor = new ModelTypeExtractor();

  @Test
  void extractsTopLevelClassAndNestedRecords() {
    var source = """
            public class InvoiceModels {
              public record InvoiceIn(String id) {}
              public record InvoiceOut(String id) {}
            }
            """;

    var names = extractor.extract("InvoiceModels.java", source);

    assertThat(names).containsExactly(
            "InvoiceModels", "InvoiceModels.InvoiceIn", "InvoiceModels.InvoiceOut");
  }

  @Test
  void includesFullyQualifiedNameWhenPackagePresent() {
    var source = """
            package cbs.nova.example;

            public class ProbeModels {
              public record ProbeIn(String value) {}
            }
            """;

    var names = extractor.extract("ProbeModels.java", source);

    assertThat(names).containsExactly(
            "cbs.nova.example.ProbeModels", "cbs.nova.example.ProbeModels.ProbeIn");
  }

  @Test
  void skipsInterfacesAndEnums() {
    var source = """
            public class MixedModels {
              public interface Marker {}
              public enum Status { ON, OFF }
              public record MixedIn(String value) {}
            }
            """;

    var names = extractor.extract("MixedModels.java", source);

    assertThat(names).containsExactly("MixedModels", "MixedModels.MixedIn");
  }

  @Test
  void emptySourceYieldsNoTypes() {
    var names = extractor.extract("Empty.java", "");
    assertThat(names).isEmpty();
  }
}
