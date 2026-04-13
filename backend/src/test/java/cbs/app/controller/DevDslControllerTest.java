package cbs.app.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.app.controller.DevDslController.DevDslExecuteRequest;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.compiler.DslScriptHost;
import cbs.dsl.compiler.DslValidator;
import cbs.dsl.compiler.ValidationError;
import cbs.dsl.runtime.DslRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

@WebMvcTest(DevDslController.class)
@ActiveProfiles("dev")
class DevDslControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SecurityFilterChain securityFilterChain;

  @MockitoBean
  private DslScriptHost scriptHost;

  @MockitoBean
  private DslValidator validator;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return 200 with OK status when script compiles and validates successfully")
  void shouldReturn200WhenScriptIsValid() throws Exception {
    // Arrange
    String validScript = """
        workflow("loan-approval") {
          states("PENDING", "APPROVED", "REJECTED")
          initial("PENDING")
          terminal("APPROVED", "REJECTED")
        }
        event("approve-event")
        transaction("approve-tx")
        """;

    DslRegistry registry = new DslRegistry();

    WorkflowDefinition wf = mockWorkflow("loan-approval");
    EventDefinition evt = mockEvent("approve-event");
    TransactionDefinition tx = mockTransaction("approve-tx");
    registry.register(wf);
    registry.register(evt);
    registry.register(tx);

    when(scriptHost.eval(validScript, "input")).thenReturn(registry);
    when(validator.validate(registry, "input")).thenReturn(List.of());

    DevDslExecuteRequest request = new DevDslExecuteRequest(validScript, null, null, null, null);

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.workflows").isArray())
        .andExpect(jsonPath("$.workflows[0].code").value("loan-approval"))
        .andExpect(jsonPath("$.events[0].code").value("approve-event"))
        .andExpect(jsonPath("$.transactions[0].code").value("approve-tx"))
        .andExpect(jsonPath("$.executionSimulation").doesNotExist());
  }

  @Test
  @DisplayName("Should return 200 with empty lists when valid script has no definitions")
  void shouldReturn200WithEmptyListsWhenScriptHasNoDefinitions() throws Exception {
    String emptyScript = "// no definitions";
    DslRegistry registry = new DslRegistry();

    when(scriptHost.eval(emptyScript, "input")).thenReturn(registry);
    when(validator.validate(registry, "input")).thenReturn(List.of());

    DevDslExecuteRequest request = new DevDslExecuteRequest(emptyScript, null, null, null, null);

    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.workflows").isEmpty())
        .andExpect(jsonPath("$.events").isEmpty())
        .andExpect(jsonPath("$.transactions").isEmpty())
        .andExpect(jsonPath("$.massOperations").isEmpty());
  }

  @Test
  @DisplayName("Should return 422 with errors when validation fails")
  void shouldReturn422WhenValidationFails() throws Exception {
    // Arrange
    String invalidScript = "workflow(\"bad\") { initial(\"MISSING\") }";

    DslRegistry registry = new DslRegistry();
    WorkflowDefinition wf = mockWorkflowWithBadInitial("bad");
    registry.register(wf);

    when(scriptHost.eval(invalidScript, "input")).thenReturn(registry);
    when(validator.validate(registry, "input"))
        .thenReturn(List.of(
            new ValidationError("input", "Workflow 'bad': initial state 'MISSING' not in states")));

    DevDslExecuteRequest request = new DevDslExecuteRequest(invalidScript, null, null, null, null);

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("INVALID"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0].file").value("input"))
        .andExpect(jsonPath("$.errors[0].message")
            .value("Workflow 'bad': initial state 'MISSING' not in states"));
  }

  @Test
  @DisplayName("Should return 422 with error when script evaluation throws IllegalStateException")
  void shouldReturn422WhenScriptEvaluationFails() throws Exception {
    // Arrange
    String badSyntaxScript = "this is not valid kotlin";

    when(scriptHost.eval(badSyntaxScript, "input"))
        .thenThrow(
            new IllegalStateException("Script evaluation failed for 'input': unexpected token"));

    DevDslExecuteRequest request =
        new DevDslExecuteRequest(badSyntaxScript, null, null, null, null);

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("INVALID"))
        .andExpect(jsonPath("$.errors[0].file").value("input"))
        .andExpect(jsonPath("$.errors[0].message")
            .value("Script evaluation failed for 'input': unexpected token"));
  }

  @Test
  @DisplayName("Should return 422 with multiple errors when validation finds many issues")
  void shouldReturn422WithMultipleErrorsWhenValidationFindsManyIssues() throws Exception {
    // Arrange
    String script = "workflow(\"multi-bad\") { initial(\"X\") }";

    DslRegistry registry = new DslRegistry();
    WorkflowDefinition wf = mockWorkflowWithBadInitial("multi-bad");
    registry.register(wf);

    when(scriptHost.eval(script, "input")).thenReturn(registry);
    when(validator.validate(registry, "input"))
        .thenReturn(List.of(
            new ValidationError("input", "Error 1"), new ValidationError("input", "Error 2")));

    DevDslExecuteRequest request = new DevDslExecuteRequest(script, null, null, null, null);

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("INVALID"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors.length()").value(2));
  }

  @Test
  @DisplayName("Should include massOperations in success response")
  void shouldIncludeMassOperationsInSuccessResponse() throws Exception {
    // Arrange
    String script = "massOperation(\"bulk-notify\") { ... }";

    DslRegistry registry = new DslRegistry();
    MassOperationDefinition massOp = mockMassOperation("bulk-notify");
    registry.register(massOp);

    when(scriptHost.eval(script, "input")).thenReturn(registry);
    when(validator.validate(registry, "input")).thenReturn(List.of());

    DevDslExecuteRequest request = new DevDslExecuteRequest(script, null, null, null, null);

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.massOperations[0].code").value("bulk-notify"));
  }

  @Test
  @DisplayName("Should return 400 when dslContent is blank")
  void shouldReturn400WhenDslContentIsBlank() throws Exception {
    // Arrange
    DevDslExecuteRequest request =
        new DevDslExecuteRequest("", "TEST", "SUBMIT", Map.of("amount", 1000), "dev-user");

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("INVALID"))
        .andExpect(
            jsonPath("$.errors[0].message").value("dslContent is required and must not be blank"));
  }

  @Test
  @DisplayName("Should include executionSimulation when eventCode is provided")
  void shouldIncludeExecutionSimulationWhenEventCodeProvided() throws Exception {
    // Arrange
    String script = "event(\"approve-event\") { }";

    DslRegistry registry = new DslRegistry();
    EventDefinition evt = mockEvent("approve-event");
    registry.register(evt);

    when(scriptHost.eval(script, "input")).thenReturn(registry);
    when(validator.validate(registry, "input")).thenReturn(List.of());

    DevDslExecuteRequest request = new DevDslExecuteRequest(
        script, "approve-event", "SUBMIT", Map.of("amount", 1000), "dev-user");

    // Act & Assert
    mockMvc
        .perform(post("/dev/dsl/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.executionSimulation.eventCode").value("approve-event"))
        .andExpect(jsonPath("$.executionSimulation.action").value("SUBMIT"))
        .andExpect(jsonPath("$.executionSimulation.userId").value("dev-user"))
        .andExpect(jsonPath("$.executionSimulation.parameters.amount").value(1000))
        .andExpect(jsonPath("$.executionSimulation.status").value("SIMULATED"));
  }

  // -- Test helpers --

  private WorkflowDefinition mockWorkflow(String code) {
    WorkflowDefinition wf = Mockito.mock(WorkflowDefinition.class);
    when(wf.getCode()).thenReturn(code);
    when(wf.getStates()).thenReturn(List.of("PENDING", "APPROVED", "REJECTED"));
    when(wf.getInitial()).thenReturn("PENDING");
    when(wf.getTerminalStates()).thenReturn(List.of("APPROVED", "REJECTED"));
    when(wf.getTransitions()).thenReturn(List.of());
    return wf;
  }

  private WorkflowDefinition mockWorkflowWithBadInitial(String code) {
    WorkflowDefinition wf = Mockito.mock(WorkflowDefinition.class);
    when(wf.getCode()).thenReturn(code);
    when(wf.getStates()).thenReturn(List.of("A", "B"));
    when(wf.getInitial()).thenReturn("MISSING");
    when(wf.getTerminalStates()).thenReturn(List.of("B"));
    when(wf.getTransitions()).thenReturn(List.of());
    return wf;
  }

  private EventDefinition mockEvent(String code) {
    EventDefinition evt = Mockito.mock(EventDefinition.class);
    when(evt.getCode()).thenReturn(code);
    return evt;
  }

  private TransactionDefinition mockTransaction(String code) {
    TransactionDefinition tx = Mockito.mock(TransactionDefinition.class);
    when(tx.getCode()).thenReturn(code);
    return tx;
  }

  private MassOperationDefinition mockMassOperation(String code) {
    MassOperationDefinition massOp = Mockito.mock(MassOperationDefinition.class);
    when(massOp.getCode()).thenReturn(code);
    return massOp;
  }
}
