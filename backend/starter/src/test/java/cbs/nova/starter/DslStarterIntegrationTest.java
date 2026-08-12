package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.controllers.DslRuntimeResource;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
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

    var recorder = new RunScopedExternalCallRecorder(null);
    var traceCollector = DslConfig.dslConfig().executionTraceCollector();
    var contextFactory = new ContextFactory();
    var dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
    var previewProperties = new CbsNovaPreviewProperties(null, null);
    var previewPipe = new PreviewDslPipe(recorder, contextFactory, dryRunLoggingContext, null,
            previewProperties, traceCollector);
    var runPipe = new RunDslPipe(contextFactory, traceCollector);
    var explainPipe = new ExplainDslPipe(recorder, contextFactory, dryRunLoggingContext,
            previewProperties, traceCollector);
    var runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);
    var resource = new DslRuntimeResource(runtime, contextFactory, recorder);
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
            .andExpect(jsonPath("$.astTree").isNotEmpty());
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
