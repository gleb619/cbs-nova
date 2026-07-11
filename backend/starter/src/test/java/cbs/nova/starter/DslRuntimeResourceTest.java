package cbs.nova.starter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.DslException;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.controllers.DslRuntimeResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

class DslRuntimeResourceTest {

  private final DslRuntime dslRuntime = mock(DslRuntime.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
            .standaloneSetup(new DslRuntimeResource(dslRuntime, new ContextFactory()))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  void previewReturns200OnSuccess() throws Exception {
    PreviewReport report = new PreviewReport(
            "Ping",
            ExecutionMode.PREVIEW,
            true,
            "pong",
            List.of("started: Ping", "mode: PREVIEW", "completed successfully"),
            List.of(),
            Map.of());
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Ping"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Ping")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"hello\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ping"))
            .andExpect(jsonPath("$.mode").value("PREVIEW"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.output").value("pong"))
            .andExpect(jsonPath("$.executionTrace[0]").value("started: Ping"))
            .andExpect(jsonPath("$.callCounts").isMap());
  }

  @Test
  void previewReturns422OnFailure() throws Exception {
    doReturn(Result.failure(new RuntimeException("boom")))
            .when(dslRuntime)
            .preview(eq("Fail"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Fail")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"x\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("EXECUTION_FAILED"))
            .andExpect(jsonPath("$.message").value("boom"))
            .andExpect(jsonPath("$.entityName").value("Fail"))
            .andExpect(jsonPath("$.runId").exists())
            .andExpect(jsonPath("$.exceptionId").exists());
  }

  @Test
  void runReturns200OnSuccess() throws Exception {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isOk());
  }

  @Test
  void runReturns422OnGenericFailure() throws Exception {
    doReturn(Result.failure(new RuntimeException("exec error")))
            .when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("EXECUTION_FAILED"))
            .andExpect(jsonPath("$.message").value("exec error"))
            .andExpect(jsonPath("$.runId").exists());
  }

  @Test
  void runReturns422WithDslExceptionFields() throws Exception {
    var ex = new DslException("run-abc", DslErrorCode.ENTITY_NOT_FOUND, "not found");
    doReturn(Result.failure(ex)).when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
            .andExpect(jsonPath("$.runId").value("run-abc"))
            .andExpect(jsonPath("$.exceptionId").isString());
  }

  @Test
  void explainReturns200WithReport() throws Exception {
    ExplainReport report = new ExplainReport(
            "P", "desc", "graph TD;", "<plantuml/>", "<bpmn/>",
            List.of(), List.of(), Map.of(), null, null);
    doReturn(report).when(dslRuntime).explain(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/explain/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"in\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("P"))
            .andExpect(jsonPath("$.description").value("desc"));
  }
}
