package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.controllers.DslRuntimeResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DslStarterIntegrationTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(
                    Dsl.process("LoanDisbursement")
                            .input(String.class)
                            .output(String.class)
                            .execute(ctx -> Result.success("disbursed"))
                            .build());

    var runtime = new DevDslRuntime(new ExternalCallTracker(), new ExecutionTraceCollector(),
            new ContextFactory());
    var resource = new DslRuntimeResource(runtime, new ContextFactory());
    mockMvc = MockMvcBuilders.standaloneSetup(resource)
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void previewEndpointReturnsDisbursedResult() throws Exception {
    mockMvc
            .perform(
                    post("/api/dsl/preview/LoanDisbursement")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"customer-1\"}"))
            .andExpect(status().isOk())
            .andExpect(result -> {
              String body = result.getResponse().getContentAsString();
              assert body.contains("disbursed") : "Expected 'disbursed' in: " + body;
            });
  }

  @Test
  void runEndpointSucceeds() throws Exception {
    mockMvc
            .perform(
                    post("/api/dsl/run/LoanDisbursement")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"customer-2\"}"))
            .andExpect(status().isOk());
  }

  @Test
  void explainEndpointReturnsDiagram() throws Exception {
    mockMvc
            .perform(
                    post("/api/dsl/explain/LoanDisbursement")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"customer-3\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDisbursement"))
            .andExpect(jsonPath("$.mermaidDiagram").isNotEmpty());
  }

  @Test
  void unknownProcessReturns422() throws Exception {
    mockMvc
            .perform(
                    post("/api/dsl/preview/UnknownProcess")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"x\"}"))
            .andExpect(status().isUnprocessableEntity());
  }
}
