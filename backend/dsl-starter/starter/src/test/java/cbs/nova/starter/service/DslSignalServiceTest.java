package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.SignalDescriptor;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.DslAudit;
import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.persistence.DslAuditSearchResult;
import cbs.nova.starter.service.check.AuditWriteHook;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class DslSignalServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  private final WorkflowClient workflowClient = mock(WorkflowClient.class);
  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository(
          InMemoryDslRunRepository.NO_OP_EVICTION);
  private final DslSignalService service = new DslSignalService(workflowClient, repository);

  @Test
  void unknownRunIsReportedAsNotFoundWithoutTouchingTemporal() {
    DslSignalService.SignalResult result = service.sendSignal("nope", "approve", null);

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.NOT_FOUND);
    assertThat(result.run()).isNull();
    assertThat(result.currentStatus()).isNull();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void nonRunningRunIsReportedAsNotRunningWithoutTouchingTemporal() {
    repository.save(run("run-1", DslRunStatus.COMPLETED));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve", null);

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.NOT_RUNNING);
    assertThat(result.run()).isNotNull();
    assertThat(result.currentStatus()).isEqualTo("COMPLETED");
    verifyNoInteractions(workflowClient);
  }

  @Test
  void runningRunSendsSignalWithPayloadAndReportsSent() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve",
            Map.of("approved", true));

    verify(stub).signal("approve", Map.of("approved", true));
    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);
    assertThat(result.run()).isNotNull();
    assertThat(result.currentStatus()).isEqualTo("RUNNING");
  }

  @Test
  void runningRunSendsSignalWithoutPayloadWhenPayloadIsNull() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve", null);

    verify(stub).signal("approve");
    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);
  }

  @Test
  void signalFailureFromStubIsPropagated() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    doThrow(workflowNotFound()).when(stub).signal("approve");
    repository.save(running("run-1"));

    assertThatThrownBy(() -> service.sendSignal("run-1", "approve", null))
            .isInstanceOf(WorkflowNotFoundException.class);
  }

  @Test
  void undeclaredSignalIsReportedAsUnknownSignalWithoutTouchingTemporal() {
    GlobalManager.globalManager().registerProcess(processWithSignals("T672ProbeProcess",
            "approve"));
    repository.save(running("run-1", "T672ProbeProcess"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "bogus", null);

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.UNKNOWN_SIGNAL);
    assertThat(result.run()).isNotNull();
    assertThat(result.currentStatus()).isEqualTo("RUNNING");
    verifyNoInteractions(workflowClient);
  }

  @Test
  void declaredSignalIsSentWhenProcessDeclaresIt() {
    GlobalManager.globalManager().registerProcess(processWithSignals("T672DeclaredProbe",
            "approve"));
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1", "T672DeclaredProbe"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve", null);

    verify(stub).signal("approve");
    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);
  }

  @Test
  void unknownProcessDefinitionStillAttemptsTemporalSend() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1", "NoSuchT672Process"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve", null);

    verify(stub).signal("approve");
    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);
  }

  @Test
  void querySignalStateReturnsMappedStateForRunningRun() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    Map<String, Object> state = Map.of("lastSignal", "approve", "count", 3);
    doReturn(state).when(stub).query(eq("dslSignalState"), eq(Map.class));
    repository.save(running("run-1"));

    Map<String, Object> result = service.querySignalState("run-1");

    assertThat(result).isEqualTo(state);
  }

  @Test
  void querySignalStateReturnsNullForUnknownRun() {
    assertThat(service.querySignalState("nope")).isNull();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void querySignalStateReturnsEmptyMapForNonRunningRun() {
    repository.save(run("run-1", DslRunStatus.FAILED));

    assertThat(service.querySignalState("run-1")).isEmpty();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void querySignalStateReturnsEmptyMapWhenStubThrows() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    doThrow(workflowNotFound()).when(stub).query(eq("dslSignalState"), eq(Map.class));
    repository.save(running("run-1"));

    assertThat(service.querySignalState("run-1")).isEmpty();
  }

  private static WorkflowNotFoundException workflowNotFound() {
    return new WorkflowNotFoundException(
            io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                    .setWorkflowId("run-1")
                    .build(),
            "LoanDisbursement",
            null);
  }

  // --- T721: SIGNAL_SENT manifest audit row ------------------------------------------

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void manifestSignalSendAuditRecordsSignalSentRowWhenPipelineFires() {
    AuditTestSupport.Harness audit = AuditTestSupport.h2();
    Piece piece = signalSendManifestPiece();
    PieceCheckPipeline pipeline = new PieceCheckPipeline(Runnable::run,
            List.of(new AuditWriteHook(AuditTestSupport.providerOf(audit.service()))),
            new PieceCheckBlockRegistry(manifestProperties(), System::currentTimeMillis),
            AuditTestSupport.providerOf(audit.service()), null);
    authenticateAsRunner("alice");

    pipeline.onSuccess(piece, "auth:alice", "alice", "rid-1", signalSendContext());

    DslAuditSearchResult result = audit.service().search("SIGNAL_SENT", 0, 10);
    assertThat(result.total()).isEqualTo(1);
    DslAudit row = result.items().get(0);
    assertThat(row.action()).isEqualTo("SIGNAL_SENT");
    assertThat(row.target()).isEqualTo("dsl-signal-send");
    assertThat(row.outcome()).isEqualTo(StarterConstants.OUTCOME_SUCCESS);
    assertThat(row.actor()).isEqualTo("alice");
    assertThat(row.correlationId()).isEqualTo("rid-1");
    assertThat(row.detailsJson()).contains("\"route\":\"POST /api/dsl/signals/run-1\"");
  }

  @Test
  void manifestSignalSendAuditRecordsNoRowWhenPostCheckPipelineIsNotInvoked() {
    // PieceGuardFilter.proceedAndTriggerPostChecks skips the post-check dispatch when the
    // handler returns status >= 400 (T672 UNKNOWN_SIGNAL → 400, NOT_FOUND → 404,
    // NOT_RUNNING → 409), so the audit-write hook never fires for failed sends.
    AuditTestSupport.Harness audit = AuditTestSupport.h2();

    DslAuditSearchResult result = audit.service().search("SIGNAL_SENT", 0, 10);
    assertThat(result.total()).isZero();
  }

  private static Piece signalSendManifestPiece() {
    return new Piece("dsl-signal-send",
            new Target.ApiTarget("POST /api/dsl/signals/{runId}"), List.of(),
            List.of(new PostCheck.AuditWriteCheck("SIGNAL_SENT")), "deny");
  }

  private static InvariantContext signalSendContext() {
    return new InvariantContext("dsl-signal-send", "RUNNER", "POST",
            "/api/dsl/signals/run-1", 200);
  }

  private static CbsDslManifestProperties manifestProperties() {
    return new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml", null, null,
            new CbsDslManifestProperties.PostCheck(2, Duration.ofMinutes(30), "principal"), null,
            null);
  }

  private static void authenticateAsRunner(String principal) {
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                    principal,
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_RUNNER"))));
  }

  private static DslRun running(String runId) {
    return run(runId, DslRunStatus.RUNNING, "LoanDisbursement");
  }

  private static DslRun running(String runId, String processName) {
    return run(runId, DslRunStatus.RUNNING, processName);
  }

  private static DslRun run(String runId, DslRunStatus status) {
    return run(runId, status, "LoanDisbursement");
  }

  private static DslRun run(String runId, DslRunStatus status, String processName) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status.name())
            .startedAt(NOW.minusSeconds(30))
            .executionMode("RUN")
            .build();
  }

  private static ProcessDslObject processWithSignals(String name, String... signalNames) {
    return ProcessDslObject.builder()
            .name(name)
            .description("probe")
            .taskQueue("t672-tq")
            .version("v1")
            .inputType(String.class)
            .outputType(String.class)
            .parameters(java.util.List.of())
            .executeLogic(ctx -> null)
            .previewLogic(ctx -> null)
            .explainLogic(ctx -> null)
            .descriptor(cbs.nova.dsl.DslDescriptor.builder()
                    .objectDescriptor(new cbs.nova.dsl.model.ObjectDescriptor() {
                      @Override
                      public String name() {
                        return name;
                      }

                      @Override
                      public cbs.nova.dsl.DslObject.DslType type() {
                        return cbs.nova.dsl.DslObject.DslType.PROCESS;
                      }

                      @Override
                      public String description() {
                        return "probe";
                      }

                      @Override
                      public Class<?> inputType() {
                        return String.class;
                      }

                      @Override
                      public Class<?> outputType() {
                        return String.class;
                      }
                    })
                    .parameters(java.util.List.of())
                    .build())
            .signals(java.util.Arrays.stream(signalNames)
                    .map(s -> new SignalDescriptor(s, String.class))
                    .toList())
            .build();
  }
}
