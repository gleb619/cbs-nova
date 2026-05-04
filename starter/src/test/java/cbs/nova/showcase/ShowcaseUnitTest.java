package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.service.ContextEncryptionService;
import cbs.nova.service.EventExecutionService;
import cbs.nova.service.WorkflowExecutor;
import cbs.nova.service.WorkflowResolver;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Unit-style tests that verify the DSL → registry → execution chain with a mocked Temporal client.
 */
class ShowcaseUnitTest extends ShowcaseTestBase {

  private EventExecutionService eventExecutionService;

  @BeforeEach
  void setUp() {
    buildEventExecutionService();
  }

  private void buildEventExecutionService() {
    WorkflowResolver workflowResolver = new WorkflowResolver(dslRegistry);

    WorkflowClient workflowClient = mock(WorkflowClient.class);
    WorkflowStub workflowStub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub(anyString(), any(WorkflowOptions.class)))
        .thenReturn(workflowStub);
    when(workflowStub.start(any())).thenReturn(WorkflowExecution.newBuilder().build());
    when(workflowStub.getResult(WorkflowExecutionResponse.class))
        .thenReturn(new WorkflowExecutionResponse(42L, "DONE"));

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowClient);
    ReflectionTestUtils.setField(workflowExecutor, "taskQueue", "TEST_TASK_QUEUE");

    ContextEncryptionService encryptionService = new ContextEncryptionService(new ObjectMapper());

    eventExecutionService = new EventExecutionService(
        workflowResolver, workflowExecutor, encryptionService, dslRegistry);
  }

  @Test
  @DisplayName("Should compile DSL files in Gradle container and register definitions")
  void shouldCompileDslInGradleContainerAndRegisterDefinitions() {
    assertThat(dslRegistry.getEvents()).containsKey("SAMPLE_EVENT_DSL");
    assertThat(dslRegistry.getTransactions()).containsKey("SAMPLE_TRANSACTION_DSL");
    assertThat(dslRegistry.getWorkflows()).containsKey("DSL_TEST_WF");
  }

  @Test
  @DisplayName("Should execute DSL event context block with helper resolution")
  void shouldExecuteDslEventContextBlockWithHelperResolution() {
    EventDefinition eventDef = dslRegistry.resolveEvent("SAMPLE_EVENT_DSL");

    EnrichmentContext ctx =
        new EnrichmentContext("SAMPLE_EVENT_DSL", 0L, "testUser", "dev", Map.of("name", "PoC"));
    ctx.setHelperResolver(helperResolver());

    eventDef.getContextBlock().accept(ctx);

    assertThat(ctx.getEnrichment()).containsKey("enriched");
    assertThat(ctx.getEnrichment().get("enriched")).isEqualTo(Map.of("result", "PoC!"));
  }

  @Test
  @DisplayName("Should execute DSL transaction directly")
  void shouldExecuteDslTransactionDirectly() {
    TransactionDefinition txDef = dslRegistry.resolveTransaction("SAMPLE_TRANSACTION_DSL");

    var input = new TransactionInput(
        Map.of("name", "PoC"), "SAMPLE_TRANSACTION_DSL", null, "dev");
    var output = txDef.execute(input);

    assertThat(output.result()).containsEntry("greeting", "DSL TX says hello to PoC");
  }

  @Test
  @DisplayName("Should execute DSL event through EventExecutionService")
  void shouldExecuteDslEventThroughEventExecutionService() {
    var request = new EventExecutionRequest(
        "DSL_TEST_WF", "SAMPLE_EVENT_DSL", "testUser", Map.of("name", "PoC"));

    EventExecutionResponse response = eventExecutionService.execute(request);

    assertThat(response).isNotNull();
    assertThat(response.executionId()).isEqualTo(42L);
    assertThat(response.status()).isEqualTo("DONE");
  }
}
