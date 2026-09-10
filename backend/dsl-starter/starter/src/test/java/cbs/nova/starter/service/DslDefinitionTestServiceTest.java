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
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.entity.DslDefinitionTestEntity;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.model.DefinitionTestCase;
import cbs.nova.starter.model.DefinitionTestCaseResult;
import cbs.nova.starter.model.DefinitionTestRunReport;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.persistence.DslDefinitionTestRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
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
  void listMapsStoredJsonPayloadsIntoDto() throws Exception {
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            new DslDefinitionTestEntity(1L, "LoanDisbursement", "case-1",
                    "{\"x\":1}", "{\"y\":2}", Instant.now(), Instant.now())));

    List<DefinitionTestCase> cases = service.list("LoanDisbursement");

    assertThat(cases).hasSize(1);
    assertThat(cases.get(0).caseName()).isEqualTo("case-1");
    assertThat(cases.get(0).input().toString()).contains("\"x\":1");
    assertThat(cases.get(0).expectedOutput().toString()).contains("\"y\":2");
  }

  @Test
  void listOnUnknownDefinitionThrowsNotFound() {
    assertThatThrownBy(() -> service.list("ghost"))
            .isInstanceOf(DefinitionNotFoundException.class)
            .hasMessageContaining("ghost");
    verify(repository, never()).listForDefinition(anyString());
  }

  @Test
  void replaceAllPersistsRowsAndReturnsStoredSet() throws Exception {
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            new DslDefinitionTestEntity(1L, "LoanDisbursement", "case-1",
                    "{\"x\":1}", "{\"y\":2}", Instant.now(), Instant.now())));

    List<DefinitionTestCase> result = service.replaceAll("LoanDisbursement", List.of(
            new DefinitionTestCase("case-1", json("{\"x\":1}"), json("{\"y\":2}"))));

    assertThat(result).hasSize(1);
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
            entity("pass", "{\"x\":1}", "{\"y\":2}", now),
            entity("fail", "{\"x\":3}", "{\"y\":9}", now),
            entity("boom", "{\"x\":4}", "{\"y\":5}", now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenReturn(RuntimeOutcome.ok(payload("{\"y\":2}")))
            .thenReturn(RuntimeOutcome.ok(payload("{\"y\":3}")))
            .thenReturn(RuntimeOutcome.error(new ErrorResponse("DSL_ERROR",
                    "boom", "LoanDisbursement", null, null)));

    DefinitionTestRunReport report = service.run("LoanDisbursement", null);

    assertThat(report.total()).isEqualTo(3);
    assertThat(report.passed()).isEqualTo(1);
    assertThat(report.failed()).isEqualTo(1);
    assertThat(report.errored()).isEqualTo(1);
    assertThat(report.cases()).extracting(DefinitionTestCaseResult::name)
            .containsExactly("pass", "fail", "boom");
    assertThat(report.cases()).extracting(DefinitionTestCaseResult::status)
            .containsExactly(DefinitionTestCaseResult.STATUS_PASS,
                    DefinitionTestCaseResult.STATUS_FAIL,
                    DefinitionTestCaseResult.STATUS_ERROR);
    verify(previewService, times(3)).preview(anyString(), any(DslRequest.class), any());
  }

  @Test
  void runFiltersByCaseSubset() {
    Instant now = Instant.now();
    when(repository.listForDefinition("LoanDisbursement")).thenReturn(List.of(
            entity("a", "{}", "{}", now),
            entity("b", "{}", "{}", now),
            entity("c", "{}", "{}", now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenReturn(RuntimeOutcome.ok(payload("{}")));

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
            entity("a", "{}", "{}", now)));
    when(previewService.preview(anyString(), any(DslRequest.class), any()))
            .thenReturn(RuntimeOutcome.ok(payload("{}")));

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

  private static DslDefinitionTestEntity entity(String name, String inputJson,
          String expectedJson, Instant now) {
    return new DslDefinitionTestEntity(null, "LoanDisbursement", name,
            inputJson, expectedJson, now, now);
  }

  private static Map<String, Object> payload(String json) {
    try {
      return new LinkedHashMap<>(new ObjectMapper().readValue(json, Map.class));
    } catch (JacksonException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private tools.jackson.databind.JsonNode json(String text) {
    try {
      return objectMapper.readTree(text);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
