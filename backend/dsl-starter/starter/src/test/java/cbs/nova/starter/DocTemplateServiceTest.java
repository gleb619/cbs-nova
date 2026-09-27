package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.service.DocTemplateService;
import org.junit.jupiter.api.Test;

/**
 * Pins the doc skeleton contract: the construct name appears twice — in the frontmatter (search
 * key) and as the leading {@code # } body heading (view title).
 */
class DocTemplateServiceTest {

  private final DocTemplateService service = new DocTemplateService();

  @Test
  void newDocCarriesNameInFrontmatterAndBodyHeading() {
    String doc = service.newDoc("LoanDisbursement");

    assertThat(doc).isEqualTo(
            "---\nname: LoanDisbursement\ndescription: \"\"\n---\n\n# LoanDisbursement\n");
  }

  @Test
  void newDocKeepsDescription() {
    assertThat(service.newDoc("Foo", "Sums values"))
            .isEqualTo("---\nname: Foo\ndescription: Sums values\n---\n\n# Foo\n");
  }

  @Test
  void newDocTrimsTheName() {
    assertThat(service.newDoc("  Foo  ")).contains("name: Foo").contains("# Foo\n");
  }

  @Test
  void newDocIsEmptyForBlankName() {
    assertThat(service.newDoc("")).isEmpty();
    assertThat(service.newDoc("   ")).isEmpty();
    assertThat(service.newDoc(null)).isEmpty();
  }

  @Test
  void ensureDocStructureFillsNameAndHeadingForAnEmptyBody() {
    String doc = service.ensureDocStructure("BatchProcessing", "");

    assertThat(doc).isEqualTo(
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# BatchProcessing\n");
  }

  @Test
  void ensureDocStructureAddsHeadingWhenBodyLacksOne() {
    String doc = service.ensureDocStructure("BatchProcessing", "Just prose.");

    assertThat(doc).isEqualTo(
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# BatchProcessing\n\nJust prose.\n");
  }

  @Test
  void ensureDocStructureKeepsAnExistingHeading() {
    String content = "---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# Batch processing\n\nJust prose.\n";

    assertThat(service.ensureDocStructure("BatchProcessing", content)).isEqualTo(content);
  }

  @Test
  void ensureDocStructureKeepsADescriptionAndExtraFrontmatterKeys() {
    String doc = service.ensureDocStructure("BatchProcessing",
            "---\ndescription: Sums values\nowner: team-a\n---\n\n# Batch processing\n");

    assertThat(doc).contains("name: BatchProcessing")
            .contains("description: Sums values")
            .contains("owner: team-a")
            .contains("# Batch processing\n");
  }

  @Test
  void ensureDocStructureKeepsAStaleNameRatherThanOverwritingIt() {
    String doc = service.ensureDocStructure("BatchProcessing",
            "---\nname: OldName\n---\n\n# OldName\n");

    // The doc's own name wins: the UI retitles the heading alongside the field,
    // so honouring the request name here would revert every rename on save.
    assertThat(doc).contains("name: OldName").doesNotContain("BatchProcessing");
  }

  @Test
  void ensureDocStructureKeepsTheDocsOwnNameOverTheRequestName() {
    String doc = service.ensureDocStructure("BatchProcessing",
            "---\nname: RenamedInUi\ndescription: d\n---\n\n# RenamedInUi\n");

    assertThat(doc).contains("name: RenamedInUi").doesNotContain("BatchProcessing");
  }

  @Test
  void ensureDocStructureFallsBackToTheRequestNameForABlankDocName() {
    String doc = service.ensureDocStructure("BatchProcessing", "---\nname: \"\"\n---\n\nprose\n");

    assertThat(doc).isEqualTo(
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# BatchProcessing\n\nprose\n");
  }

  @Test
  void ensureDocStructureLeavesContentUntouchedForABlankName() {
    assertThat(service.ensureDocStructure("", "just prose")).isEqualTo("just prose");
    assertThat(service.ensureDocStructure(null, null)).isEmpty();
  }

  @Test
  void ensureDocStructureIsIdempotent() {
    String once = service.ensureDocStructure("BatchProcessing", "prose");

    assertThat(service.ensureDocStructure("BatchProcessing", once)).isEqualTo(once);
  }

  @Test
  void newDocOutputIsAcceptedUnchanged() {
    String doc = service.newDoc("BatchProcessing");

    assertThat(service.ensureDocStructure("BatchProcessing", doc)).isEqualTo(doc);
  }
}
