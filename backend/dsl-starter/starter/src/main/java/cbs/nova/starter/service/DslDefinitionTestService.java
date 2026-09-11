package cbs.nova.starter.service;

import static cbs.nova.starter.core.StarterConstants.STATUS_TEST_CASE_ERROR;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.core.StarterConstants;
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
import cbs.nova.starter.util.JsonDeepEquals;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Authoring + execution of definition-attached test cases (T409).
 *
 * <p>
 * {@link #run} deliberately executes every case through {@link DslRuntimeService#preview} only — it
 * never calls the run path, never starts a Temporal workflow, and never creates {@code dsl_runs}
 * rows. Reports are produced from the preview outcomes and the deep-comparison of the report
 * outputs in {@link JsonDeepEquals}.
 */
@RequiredArgsConstructor
public class DslDefinitionTestService {

  private final DslDefinitionTestRepository repository;
  private final DslRuntimeService previewService;
  private final ObjectMapper objectMapper;

  public List<DefinitionTestCase> list(String definitionName) {
    requireDefinition(definitionName);
    return repository.listForDefinition(definitionName).stream()
            .map(this::toDto)
            .toList();
  }

  public List<DefinitionTestCase> replaceAll(String definitionName,
          List<DefinitionTestCase> cases) {
    requireDefinition(definitionName);
    Instant now = Instant.now();
    List<DslDefinitionTestEntity> entities = cases.stream()
            .map(c -> new DslDefinitionTestEntity(null, definitionName, c.caseName(),
                    writeJson(c.input()), writeJson(c.expectedOutput()), now, now))
            .toList();
    repository.replaceAll(definitionName, entities);
    return repository.listForDefinition(definitionName).stream()
            .map(this::toDto)
            .toList();
  }

  /**
   * Runs stored test cases through the preview pipeline and returns a per-case report. When
   * {@code caseNames} is non-null/non-empty the run is restricted to that subset (unknown names are
   * ignored). 404 when the definition is not published.
   */
  public DefinitionTestRunReport run(String definitionName, @Nullable Set<String> caseNames) {
    requireDefinition(definitionName);
    List<DslDefinitionTestEntity> stored = repository.listForDefinition(definitionName);
    if (caseNames != null && !caseNames.isEmpty()) {
      stored = stored.stream().filter(c -> caseNames.contains(c.caseName())).toList();
    }

    List<DefinitionTestCaseResult> results = stored.stream()
            .map(caseEntity -> execute(definitionName, caseEntity))
            .toList();
    int passed = (int) results.stream()
            .filter(r -> r.status() == DefinitionTestCaseStatus.PASS).count();
    int failed = (int) results.stream()
            .filter(r -> r.status() == DefinitionTestCaseStatus.FAIL).count();
    int errored = (int) results.stream()
            .filter(r -> r.status() == DefinitionTestCaseStatus.ERROR).count();
    return new DefinitionTestRunReport(results.size(), passed, failed, errored, results);
  }

  private DefinitionTestCaseResult execute(String definitionName, DslDefinitionTestEntity entity) {
    PreviewReport expected = readReport(entity.expectedOutputJson());
    long start = System.nanoTime();
    try {
      DslRequest input = readRequest(entity.inputJson());
      RuntimeOutcome outcome = previewService.preview(definitionName, input, null);
      long durationMs = elapsedMillis(start);
      if (!outcome.success()) {
        return error(entity.caseName(), expected, durationMs, outcome.error());
      }
      PreviewReport actual = (PreviewReport) outcome.value();
      boolean match = JsonDeepEquals.deepEquals(toJsonNode(actual.output()),
              toJsonNode(expected.output()));
      return new DefinitionTestCaseResult(entity.caseName(),
              match ? DefinitionTestCaseStatus.PASS : DefinitionTestCaseStatus.FAIL,
              actual, expected, durationMs, null);
    } catch (Exception e) {
      long durationMs = elapsedMillis(start);
      ErrorResponse diagnostics = new ErrorResponse(STATUS_TEST_CASE_ERROR, e.getMessage(),
              definitionName, null, null, null);
      return new DefinitionTestCaseResult(entity.caseName(), DefinitionTestCaseStatus.ERROR,
              null, expected, durationMs, diagnostics);
    }
  }

  private static DefinitionTestCaseResult error(String name, PreviewReport expected,
          long durationMs,
          ErrorResponse error) {
    return new DefinitionTestCaseResult(name, DefinitionTestCaseStatus.ERROR, null, expected,
            durationMs, error);
  }

  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private DefinitionTestCase toDto(DslDefinitionTestEntity entity) {
    return new DefinitionTestCase(entity.caseName(), readRequest(entity.inputJson()),
            readReport(entity.expectedOutputJson()));
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Unable to serialize test-case payload", e);
    }
  }

  private DslRequest readRequest(String json) {
    try {
      return objectMapper.readValue(json, DslRequest.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Unable to parse stored test-case JSON", e);
    }
  }

  private PreviewReport readReport(String json) {
    try {
      return objectMapper.readValue(json, PreviewReport.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Unable to parse stored test-case JSON", e);
    }
  }

  private JsonNode toJsonNode(Object value) {
    return value == null ? null : objectMapper.valueToTree(value);
  }

  private static void requireDefinition(String definitionName) {
    GlobalManager gm = GlobalManager.globalManager();
    boolean exists = gm.findProcess(definitionName).isPresent()
            || gm.findTransaction(definitionName).isPresent()
            || gm.findGeneratedProcess(definitionName).isPresent();
    if (!exists) {
      throw new DefinitionNotFoundException(definitionName);
    }
  }
}
