package cbs.nova.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

@WebMvcTest({EventController.class, EventExceptionHandler.class})
@Import(EventControllerTest.TestConfig.class)
class EventControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private SecurityFilterChain securityFilterChain;

  @MockitoBean
  private EventService eventService;

  @Test
  @DisplayName("Should return 200 with execution response when event is executed successfully")
  void shouldReturn200WhenEventExecutesSuccessfully() throws Exception {
    // Arrange
    EventExecutionRequest request =
        new EventExecutionRequest("loan-approval", "approve", "admin1", Map.of("amount", 1000));
    EventExecutionResponse response = new EventExecutionResponse(1L, "ACTIVE");
    when(eventService.execute(any(EventExecutionRequest.class))).thenReturn(response);

    // Act & Assert
    mockMvc
        .perform(post("/api/events/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executionId").value(1))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("Should return 404 when workflow or event definition is not found")
  void shouldReturn404WhenDefinitionNotFound() throws Exception {
    // Arrange
    EventExecutionRequest request =
        new EventExecutionRequest("unknown-workflow", "approve", "admin1", Map.of());
    when(eventService.execute(any(EventExecutionRequest.class)))
        .thenThrow(new EntityNotFoundException("Workflow", "unknown-workflow"));

    // Act & Assert
    mockMvc
        .perform(post("/api/events/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Workflow not found: code=unknown-workflow"));
  }

  @Test
  @DisplayName("Should return 422 when service throws IllegalArgumentException")
  void shouldReturn422WhenIllegalArgument() throws Exception {
    // Arrange
    EventExecutionRequest request =
        new EventExecutionRequest("loan-approval", "approve", "admin1", Map.of());
    when(eventService.execute(any(EventExecutionRequest.class)))
        .thenThrow(new IllegalArgumentException("Context evaluation failed"));

    // Act & Assert
    mockMvc
        .perform(post("/api/events/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("Context evaluation failed"));
  }

  @Test
  @DisplayName("Should return 200 when request has no JWT token (security is mocked)")
  void shouldReturn200WhenNoJwtBecauseSecurityIsMocked() throws Exception {
    // Arrange — security is mocked, so no auth check is performed
    EventExecutionRequest request =
        new EventExecutionRequest("loan-approval", "approve", "admin1", Map.of());
    EventExecutionResponse response = new EventExecutionResponse(1L, "ACTIVE");
    when(eventService.execute(any(EventExecutionRequest.class))).thenReturn(response);

    // Act & Assert — 200 because SecurityFilterChain is a mock (no auth enforcement)
    mockMvc
        .perform(post("/api/events/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should return 422 when workflowCode is blank")
  void shouldReturn422WhenWorkflowCodeIsBlank() throws Exception {
    // Arrange
    EventExecutionRequest request = new EventExecutionRequest("", "approve", "admin1", Map.of());

    // Act & Assert
    mockMvc
        .perform(post("/api/events/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").exists());
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
