package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.service.DslDocsService;
import cbs.nova.starter.service.DocNameResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocNameResolverTest {

  private final DslDocsService docsService = mock(DslDocsService.class);
  private final DocNameResolver resolver = new DocNameResolver(docsService);

  @Test
  void prefersKebabCasePathWhenItExists() {
    when(docsService.docExists("batch-processing.md")).thenReturn(true);

    Optional<String> path = resolver.existingDocPath("BatchProcessing");

    assertThat(path).contains("batch-processing.md");
  }

  @Test
  void fallsBackToRawNameWhenKebabMissing() {
    when(docsService.docExists("batch-processing.md")).thenReturn(false);
    when(docsService.docExists("BatchProcessing.md")).thenReturn(true);

    Optional<String> path = resolver.existingDocPath("BatchProcessing");

    assertThat(path).contains("BatchProcessing.md");
  }

  @Test
  void returnsEmptyWhenNeitherDocExists() {
    when(docsService.docExists("batch-processing.md")).thenReturn(false);
    when(docsService.docExists("BatchProcessing.md")).thenReturn(false);

    assertThat(resolver.existingDocPath("BatchProcessing")).isEmpty();
  }

  @Test
  void returnsEmptyForBlankName() {
    assertThat(resolver.existingDocPath(" ")).isEmpty();
  }

  @Test
  void defaultDocPathIsKebabCase() {
    assertThat(resolver.defaultDocPath("LoanDisbursement"))
            .isEqualTo("loan-disbursement.md");
  }
}
