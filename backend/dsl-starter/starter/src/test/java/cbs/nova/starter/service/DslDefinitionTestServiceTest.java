package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.entity.DslDefinitionTestEntity;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.model.DefinitionTestCase;
import cbs.nova.starter.model.DefinitionTestCaseResult;
import cbs.nova.starter.model.DefinitionTestCaseStatus;
import cbs.nova.starter.model.DefinitionTestRunReport;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.persistence.DslDefinitionTestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class DslDefinitionTestServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private DslDefinitionTestRepository repository;
  private DslRuntimeService previewService;
  private DslDefinitionTestService service;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("LoanDisbursement")
                    .input(Object.class)
                    .output(Object.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    repository = mock(DslDefinitionTestRepository.class);
    previewService = mock(DslRuntimeService.class);
    service = new DslDefinitionTestService(repository, previewService, objectMapper);
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void listReturnsEmptyWhenNoCasesAreStored() {
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of());

    List<DefinitionTestCase> cases = service.list("LoanDisbursement");

    assertThat(cases).isEmpty();
  }

  @Test
  void listMapsStoredJsonPayloadsIntoDto() {
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("case-1", Map.of("x", 1), Map.of("y", 2), Instant.now())));

    List<DefinitionTestCase> cases = service.list("LoanDisbursement");

    assertThat(cases).hasSize(1);
    assertThat(cases.get(0).caseName()).isEqualTo("case-1");
    assertThat(cases.get(0).input()).isEqualTo(new DslRequest(Map.of("x", 1), null));
    assertThat(cases.get(0).expectedOutput().output()).isEqualTo(Map.of("y", 2));
  }

  @Test
  void listOnUnknownDefinitionThrowsNotFound() {
    assertThatThrownBy(() -> service.list("ghost"))
            .isInstanceOf(DefinitionNotFoundException.class)
            .hasMessageContaining("ghost");
    verify(repository, never()).listForDefinition(anyString());
  }

  @Test
  void replaceAllPersistsRowsAndReturnsStoredSet() {
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("case-1", Map.of("x", 1), Map.of("y", 2), Instant.now())));

    List<DefinitionTestCase> result = service.replaceAll("LoanDisbursement", List.of(
            new DefinitionTestCase("case-1", new DslRequest(Map.of("x", 1), null),
                    report(Map.of("y", 2)))));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).input()).isEqualTo(new DslRequest(Map.of("x", 1), null));
    verify(repository, times(1)).replaceAll(anyString(), any());
  }

  @Test
  void replaceAllOnUnknownDefinitionThrowsNotFound() {
    assertThatThrownBy(() -> service.replaceAll("ghost", List.of()))
            .isInstanceOf(DefinitionNotFoundException.class);
    verify(repository, never()).replaceAll(anyString(), any());
  }

  @Test
  void runExecutesEachCaseThroughPreviewAndCountsStatuses() {
    Instant now = Instant.now();
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("pass", Map.of("x", 1), Map.of("y", 2), now),
            entity("fail", Map.of("x", 3), Map.of("y", 9), now),
            entity("boom", Map.of("x", 4), Map.of("y", 5), now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenReturn(RuntimeOutcome.ok(report(Map.of("y", 2))))
            .thenReturn(RuntimeOutcome.ok(report(Map.of("y", 3))))
            .thenReturn(RuntimeOutcome.error(new ErrorResponse("DSL_ERROR",
                    "boom", "LoanDisbursement", null, null, null)));

    DefinitionTestRunReport report = service.run("LoanDisbursement", null);

    assertThat(report.total()).isEqualTo(3);
    assertThat(report.passed()).isEqualTo(1);
    assertThat(report.failed()).isEqualTo(1);
    assertThat(report.errored()).isEqualTo(1);
    assertThat(report.cases()).extracting(DefinitionTestCaseResult::name)
            .containsExactly("pass", "fail", "boom");
    assertThat(report.cases()).extracting(DefinitionTestCaseResult::status)
            .containsExactly(DefinitionTestCaseStatus.PASS,
                    DefinitionTestCaseStatus.FAIL,
                    DefinitionTestCaseStatus.ERROR);
    assertThat(report.cases().get(0).actual().output()).isEqualTo(Map.of("y", 2));
    assertThat(report.cases().get(2).actual()).isNull();
    assertThat(report.cases().get(2).diagnostics().getCode()).isEqualTo("DSL_ERROR");
    assertThat(report.cases().get(2).diagnostics().getMessage()).isEqualTo("boom");
    verify(previewService, times(3)).preview(anyString(), any(DslRequest.class), any());
  }

  @Test
  void runMarksCaseErroredWhenPreviewCallThrows() {
    Instant now = Instant.now();
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("boom", Map.of("x", 1), Map.of("y", 2), now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenThrow(new IllegalStateException("preview blew up"));

    DefinitionTestRunReport report = service.run("LoanDisbursement", null);

    assertThat(report.errored()).isEqualTo(1);
    DefinitionTestCaseResult result = report.cases().get(0);
    assertThat(result.status()).isEqualTo(DefinitionTestCaseStatus.ERROR);
    assertThat(result.actual()).isNull();
    assertThat(result.diagnostics().getCode()).isEqualTo("TEST_CASE_ERROR");
    assertThat(result.diagnostics().getMessage()).isEqualTo("preview blew up");
  }

  @Test
  void runFiltersByCaseSubset() {
    Instant now = Instant.now();
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("a", Map.of(), Map.of(), now),
            entity("b", Map.of(), Map.of(), now),
            entity("c", Map.of(), Map.of(), now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenReturn(RuntimeOutcome.ok(report(Map.of())));

    DefinitionTestRunReport report = service.run("LoanDisbursement",
            Set.of("a", "c", "missing"));

    assertThat(report.total()).isEqualTo(2);
    assertThat(report.cases()).extracting(DefinitionTestCaseResult::name)
            .containsExactly("a", "c");
    verify(previewService, times(2)).preview(anyString(), any(DslRequest.class), any());
  }

  @Test
  void runNeverTouchesDslRunsTableBecauseItUsesPreviewOnly() {
    Instant now = Instant.now();
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("a", Map.of(), Map.of(), now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenReturn(RuntimeOutcome.ok(report(Map.of())));

    service.run("LoanDisbursement", null);

    verify(previewService, times(1)).preview(anyString(), any(DslRequest.class), any());
  }

  @Test
  void runOnUnknownDefinitionThrowsNotFound() {
    assertThatThrownBy(() -> service.run("ghost", null))
            .isInstanceOf(DefinitionNotFoundException.class);
    verify(repository, never()).listForDefinition(anyString());
    verify(previewService, never()).preview(anyString(), any(DslRequest.class), any());
  }

  private static DslDefinitionTestEntity entity(String name, Object inputBody,
          Object expectedOutput, Instant now) {
    return new DslDefinitionTestEntity(null, "LoanDisbursement", name,
            writeJson(new DslRequest(inputBody, null)), writeJson(report(expectedOutput)), now,
            now);
  }

  private static PreviewReport report(Object output) {
    return new PreviewReport("LoanDisbursement", ExecutionMode.PREVIEW, true, output,
            List.of(), List.of(), Map.of(), null, List.of(), null, null);
  }

  private static String writeJson(Object value) {
    try {
      return new ObjectMapper().writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
