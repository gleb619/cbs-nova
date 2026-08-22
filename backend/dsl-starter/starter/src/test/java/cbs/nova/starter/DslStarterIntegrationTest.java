package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.config.DslRuntimeRouterConfiguration;
import cbs.nova.starter.controllers.DslRuntimeHandler;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
    var contextFactory = new ContextFactory();
    var dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
    var bufferRegistry = new DryRunLogBufferRegistry();
    var previewProperties = new CbsNovaPreviewProperties(null, null);
    var previewPipe = new PreviewDslPipe(recorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, null,
            previewProperties, new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
    var runPipe = new RunDslPipe(contextFactory, recorder, new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig());
    var explainPipe = new ExplainDslPipe(recorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, previewProperties,
            new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
    var runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);
    var handler = new DslRuntimeHandler(runtime, contextFactory);
    var router = new DslRuntimeRouterConfiguration();
    mockMvc = MockMvcBuilders.routerFunctions(router.dslRuntimeRouter(handler)).build();
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
