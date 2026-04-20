package cbs.nova.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.model.PaginatedResponse;
import cbs.nova.model.WorkflowExecutionDto;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.service.WorkflowExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

@WebMvcTest({WorkflowExecutionController.class})
@Import(WorkflowExecutionControllerTest.TestConfig.class)
class WorkflowExecutionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private SecurityFilterChain securityFilterChain;

  @MockitoBean
  private WorkflowExecutionService workflowExecutionService;

  @Test
  @DisplayName("shouldReturn200WhenListingExecutions")
  void shouldReturn200WhenListingExecutions() throws Exception {
    // Arrange
    WorkflowExecutionDto dto = WorkflowExecutionDto.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc123")
        .currentState("approved")
        .status("ACTIVE")
        .context("{}")
        .displayData("{}")
        .performedBy("admin1")
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    when(workflowExecutionService.findAll(0, 20))
        .thenReturn(new PaginatedResponse<>(List.of(dto), 1, 0, 20, 1));

    // Act & Assert
    mockMvc
        .perform(get("/api/executions"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "1"))
        .andExpect(header().string("X-Page-Number", "0"))
        .andExpect(header().string("X-Page-Size", "20"))
        .andExpect(header().string("X-Total-Pages", "1"))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].workflowCode").value("loan-approval"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  @DisplayName("shouldReturn200WhenFindingById")
  void shouldReturn200WhenFindingById() throws Exception {
    // Arrange
    WorkflowExecutionDto dto = WorkflowExecutionDto.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc123")
        .currentState("approved")
        .status("ACTIVE")
        .context("{}")
        .displayData("{}")
        .performedBy("admin1")
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    when(workflowExecutionService.findById(1L)).thenReturn(dto);

    // Act & Assert
    mockMvc
        .perform(get("/api/executions/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.workflowCode").value("loan-approval"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.performedBy").value("admin1"));
  }

  @Test
  @DisplayName("shouldReturn404WhenFindingByIdNotFound")
  void shouldReturn404WhenFindingByIdNotFound() throws Exception {
    // Arrange
    when(workflowExecutionService.findById(999L))
        .thenThrow(new EntityNotFoundException("WorkflowExecution", 999L));

    // Act & Assert
    mockMvc
        .perform(get("/api/executions/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("WorkflowExecution not found: id=999"));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
