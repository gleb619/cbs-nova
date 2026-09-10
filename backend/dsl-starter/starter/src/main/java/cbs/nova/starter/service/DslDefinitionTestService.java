package cbs.nova.starter.service;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.entity.DslDefinitionTestEntity;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.model.DefinitionTestCase;
import cbs.nova.starter.model.DefinitionTestCaseResult;
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
 * rows. Reports are produced from the preview outcomes and the deep-comparison in
 * {@link JsonDeepEquals}.
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
            .filter(r -> DefinitionTestCaseResult.STATUS_PASS.equals(r.status())).count();
    int failed = (int) results.stream()
            .filter(r -> DefinitionTestCaseResult.STATUS_FAIL.equals(r.status())).count();
    int errored = (int) results.stream()
            .filter(r -> DefinitionTestCaseResult.STATUS_ERROR.equals(r.status())).count();
    return new DefinitionTestRunReport(results.size(), passed, failed, errored, results);
  }

  private DefinitionTestCaseResult execute(String definitionName, DslDefinitionTestEntity entity) {
    JsonNode expected = readTree(entity.expectedOutputJson());
    long start = System.nanoTime();
    try {
      Object input = objectMapper.readValue(entity.inputJson(), Object.class);
      RuntimeOutcome outcome = previewService.preview(definitionName,
              new DslRequest(input, null), null);
      long durationMs = elapsedMillis(start);
      if (!outcome.success()) {
        return error(entity.caseName(), expected, durationMs, outcome.error());
      }
      JsonNode actual = objectMapper.valueToTree(outcome.value());
      boolean match = JsonDeepEquals.deepEquals(actual, expected);
      return new DefinitionTestCaseResult(entity.caseName(),
              match ? DefinitionTestCaseResult.STATUS_PASS : DefinitionTestCaseResult.STATUS_FAIL,
              actual, expected, durationMs, null);
    } catch (Exception e) {
      long durationMs = elapsedMillis(start);
      return new DefinitionTestCaseResult(entity.caseName(),
              DefinitionTestCaseResult.STATUS_ERROR,
              null, expected, durationMs, e.getMessage());
    }
  }

  private static DefinitionTestCaseResult error(String name, JsonNode expected, long durationMs,
          ErrorResponse error) {
    return new DefinitionTestCaseResult(name, DefinitionTestCaseResult.STATUS_ERROR, null, expected,
            durationMs, error);
  }

  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private DefinitionTestCase toDto(DslDefinitionTestEntity entity) {
    return new DefinitionTestCase(entity.caseName(), readTree(entity.inputJson()),
            readTree(entity.expectedOutputJson()));
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Unable to serialize test-case payload", e);
    }
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Unable to parse stored test-case JSON", e);
    }
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
