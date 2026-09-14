package cbs.nova.dsl.explain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExplainResourceFrontmatterTest {

  @Test
  void parsesSingleKeyValuePair() {
    var parsed = ExplainResourceFrontmatter.parse("""
            ---
            name: BatchProcessing
            ---

            # Body
            """);

    assertThat(parsed.metadata()).containsExactly(Map.entry("name", "BatchProcessing"));
    assertThat(parsed.body()).isEqualTo("# Body");
  }

  @Test
  void parsesAllKnownKeys() {
    var parsed = ExplainResourceFrontmatter.parse("""
            ---
            name: BatchProcessing
            description: Sums values.
            ---

            body""");

    assertThat(parsed.metadata())
            .containsEntry("name", "BatchProcessing")
            .containsEntry("description", "Sums values.");
    assertThat(parsed.body()).isEqualTo("body");
  }

  @Test
  void ignoresBlankLinesBeforeOpeningMarker() {
    var parsed = ExplainResourceFrontmatter.parse("""

            ---
            name: X
            ---

            body""");

    assertThat(parsed.metadata()).containsEntry("name", "X");
  }

  @Test
  void returnsEmptyMetadataAndFullSourceWhenNoFrontmatter() {
    var source = "# no frontmatter\njust markdown";

    var parsed = ExplainResourceFrontmatter.parse(source);

    assertThat(parsed.metadata()).isEmpty();
    assertThat(parsed.body()).isEqualTo(source);
  }

  @Test
  void ignoresCommentLinesInsideFrontmatter() {
    var parsed = ExplainResourceFrontmatter.parse("""
            ---
            # comment
            name: X
            ---
            body""");

    assertThat(parsed.metadata()).containsOnly(Map.entry("name", "X"));
  }

  @Test
  void returnsEmptyMetadataWhenOpeningMarkerWithoutClosingMarker() {
    var parsed = ExplainResourceFrontmatter.parse("""
            ---
            name: X

            no closing""");

    assertThat(parsed.metadata()).isEmpty();
    assertThat(parsed.body()).contains("no closing");
  }

  @Test
  void stripsLeadingNewlineFromBody() {
    var parsed = ExplainResourceFrontmatter.parse("""
            ---
            name: X
            ---

            first body line""");

    assertThat(parsed.body()).isEqualTo("first body line");
  }

  @Test
  void metadataIsImmutable() {
    var parsed = ExplainResourceFrontmatter.parse("""
            ---
            name: X
            ---

            body""");

    assertThatThrownBy(() -> parsed.metadata().put("y", "z"))
            .isInstanceOf(UnsupportedOperationException.class);
  }
}
