package cbs.nova.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.model.MassOperationDto;
import cbs.nova.model.MassOperationItemDto;
import cbs.nova.model.MassOperationTriggerRequest;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.service.MassOperationService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@WebMvcTest({MassOperationController.class, MassOperationExceptionHandler.class})
@Import(MassOperationControllerTest.TestConfig.class)
class MassOperationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private MassOperationService massOperationService;

  @MockitoBean
  private SecurityFilterChain securityFilterChain;

  @Test
  @DisplayName("shouldReturn201WhenTriggeringMassOperation")
  void shouldReturn201WhenTriggeringMassOperation() throws Exception {
    MassOperationDto response = MassOperationDto.builder()
        .code("daily-interest")
        .status("RUNNING")
        .temporalWorkflowId("massop-daily-interest-abc123")
        .triggerType("MANUAL")
        .performedBy("admin1")
        .dslVersion("1.0.0")
        .startedAt(OffsetDateTime.now())
        .build();

    when(massOperationService.trigger(
        new MassOperationTriggerRequest("daily-interest", "admin1", "1.0.0", null, null, null)))
        .thenReturn(response);

    mockMvc
        .perform(post("/api/mass-operations/trigger")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "massOpCode", "daily-interest",
                "performedBy", "admin1",
                "dslVersion", "1.0.0"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("daily-interest"))
        .andExpect(jsonPath("$.status").value("RUNNING"))
        .andExpect(jsonPath("$.temporalWorkflowId").value("massop-daily-interest-abc123"));
  }

  @Test
  @DisplayName("shouldReturn200WhenListingAllMassOperations")
  void shouldReturn200WhenListingAllMassOperations() throws Exception {
    MassOperationDto dto = MassOperationDto.builder()
        .id(1L)
        .code("daily-interest")
        .status("RUNNING")
        .build();

    when(massOperationService.findAll()).thenReturn(List.of(dto));

    mockMvc
        .perform(get("/api/mass-operations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].code").value("daily-interest"));
  }

  @Test
  @DisplayName("shouldReturn200WhenFindingById")
  void shouldReturn200WhenFindingById() throws Exception {
    MassOperationDto dto = MassOperationDto.builder()
        .id(1L)
        .code("daily-interest")
        .status("COMPLETED")
        .build();

    when(massOperationService.findById(1L)).thenReturn(dto);

    mockMvc
        .perform(get("/api/mass-operations/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.code").value("daily-interest"));
  }

  @Test
  @DisplayName("shouldReturn404WhenFindingByIdNotFound")
  void shouldReturn404WhenFindingByIdNotFound() throws Exception {
    when(massOperationService.findById(999L))
        .thenThrow(new EntityNotFoundException("MassOperationExecution", 999L));

    mockMvc
        .perform(get("/api/mass-operations/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("MassOperationExecution not found: id=999"));
  }

  @Test
  @DisplayName("shouldReturn200WhenFindingItemsByExecutionId")
  void shouldReturn200WhenFindingItemsByExecutionId() throws Exception {
    MassOperationItemDto item = MassOperationItemDto.builder()
        .id(10L)
        .itemKey("item-1")
        .status("COMPLETED")
        .build();

    when(massOperationService.findItems(1L)).thenReturn(List.of(item));

    mockMvc
        .perform(get("/api/mass-operations/1/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].itemKey").value("item-1"));
  }

  @Test
  @DisplayName("shouldReturn404WhenFindingItemsByExecutionIdNotFound")
  void shouldReturn404WhenFindingItemsByExecutionIdNotFound() throws Exception {
    when(massOperationService.findItems(999L))
        .thenThrow(new EntityNotFoundException("MassOperationExecution", 999L));

    mockMvc
        .perform(get("/api/mass-operations/999/items"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("MassOperationExecution not found: id=999"));
  }

  @Test
  @DisplayName("shouldReturn200WithRetriedCountWhenRetryingFailedItems")
  void shouldReturn200WithRetriedCountWhenRetryingFailedItems() throws Exception {
    when(massOperationService.retryFailedItems(1L)).thenReturn(5);

    mockMvc
        .perform(post("/api/mass-operations/1/retry"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retriedCount").value(5));
  }

  @Test
  @DisplayName("shouldReturn404WhenRetryingFailedItemsNotFound")
  void shouldReturn404WhenRetryingFailedItemsNotFound() throws Exception {
    when(massOperationService.retryFailedItems(999L))
        .thenThrow(new EntityNotFoundException("MassOperationExecution", 999L));

    mockMvc
        .perform(post("/api/mass-operations/999/retry"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("MassOperationExecution not found: id=999"));
  }

  @Test
  @DisplayName("shouldReturn422WhenMassOpCodeIsBlank")
  void shouldReturn422WhenMassOpCodeIsBlank() throws Exception {
    mockMvc
        .perform(post("/api/mass-operations/trigger")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "massOpCode", "",
                "performedBy", "admin1",
                "dslVersion", "1.0.0"))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("shouldReturn422WhenPerformedByIsBlank")
  void shouldReturn422WhenPerformedByIsBlank() throws Exception {
    mockMvc
        .perform(post("/api/mass-operations/trigger")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "massOpCode", "daily-interest",
                "performedBy", "   ",
                "dslVersion", "1.0.0"))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("shouldReturn422WhenDslVersionIsBlank")
  void shouldReturn422WhenDslVersionIsBlank() throws Exception {
    mockMvc
        .perform(post("/api/mass-operations/trigger")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "massOpCode", "daily-interest",
                "performedBy", "admin1",
                "dslVersion", ""))))
        .andExpect(status().isUnprocessableEntity());
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
