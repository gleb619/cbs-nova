package cbs.nova.starter.controller;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.DefinitionTestCase;
import cbs.nova.starter.model.DefinitionTestRunReport;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionTestService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the definition test-case surface (T409).
 *
 * <p>
 * Thin HTTP shell around {@link DslDefinitionTestService}: extracts path/query/body, delegates, and
 * maps the result. {@code /tests/run} also appends a {@code TESTS_RUN} audit entry through the
 * optional {@link DslAuditService} (present only when a {@code DataSource} is).
 */
@RequiredArgsConstructor
public class DslDefinitionTestHandler {

  private final DslDefinitionTestService service;
  private final ObjectProvider<DslAuditService> auditServiceProvider;

  public ServerResponse list(ServerRequest request) {
    List<DefinitionTestCase> cases = service.list(request.pathVariable("name"));
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(cases);
  }

  public ServerResponse replace(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DefinitionTestCase[] body = request.body(DefinitionTestCase[].class);
    List<DefinitionTestCase> written = service.replaceAll(name,
            body != null ? List.of(body) : List.of());
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(written);
  }

  public ServerResponse run(ServerRequest request) {
    String name = request.pathVariable("name");
    Set<String> subset = request.params().get("case").stream()
            .filter(c -> c != null && !c.isBlank())
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> caseSubset = subset.isEmpty() ? null : subset;

    DefinitionTestRunReport report = service.run(name, caseSubset);

    audit(request, name, report);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(report);
  }

  private void audit(ServerRequest request, String definition, DefinitionTestRunReport report) {
    DslAuditService auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    String outcome = report.errored() > 0
            ? StarterConstants.OUTCOME_FAILURE
            : StarterConstants.OUTCOME_SUCCESS;
    auditService.record(DslAuditService.currentActor(), StarterConstants.ACTION_TESTS_RUN,
            definition,
            DslAuditService.correlationIdOf(request), outcome,
            Map.of("total", report.total(), "passed", report.passed(),
                    "failed", report.failed(), "errored", report.errored()));
  }
}
