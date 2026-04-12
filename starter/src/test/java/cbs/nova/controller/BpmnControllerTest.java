package cbs.nova.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.bpmn.BpmnExporter;
import cbs.nova.bpmn.WorkflowNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BpmnController.class)
class BpmnControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private BpmnExporter bpmnExporter;

  @MockitoBean
  private SecurityFilterChain securityFilterChain;

  @Test
  @DisplayName("shouldReturn200XmlWhenWorkflowExists")
  void shouldReturn200XmlWhenWorkflowExists() throws Exception {
    when(bpmnExporter.export("loan-approval")).thenReturn("<bpmn/>");

    mockMvc
        .perform(get("/api/workflows/loan-approval/bpmn"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_XML_VALUE))
        .andExpect(content().string("<bpmn/>"));
  }

  @Test
  @DisplayName("shouldReturn404WhenWorkflowNotFound")
  void shouldReturn404WhenWorkflowNotFound() throws Exception {
    when(bpmnExporter.export("nonexistent"))
        .thenThrow(new WorkflowNotFoundException("nonexistent"));

    mockMvc.perform(get("/api/workflows/nonexistent/bpmn")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("shouldReturn501WhenFormatIsSvg")
  void shouldReturn501WhenFormatIsSvg() throws Exception {
    mockMvc
        .perform(get("/api/workflows/loan-approval/bpmn").param("format", "svg"))
        .andExpect(status().isNotImplemented());
  }
}
