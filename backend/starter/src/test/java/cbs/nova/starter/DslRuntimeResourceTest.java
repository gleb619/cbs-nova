package cbs.nova.starter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class DslRuntimeResourceTest {

  private final DslRuntime dslRuntime = mock(DslRuntime.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new DslRuntimeResource(dslRuntime))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  void previewReturns200OnSuccess() throws Exception {
    doReturn(Result.success("pong")).when(dslRuntime).preview(eq("Ping"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Ping")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"hello\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("\"pong\""));
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
            .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void explainReturnsReport() throws Exception {
    var report = new ExplainReport(
            "Ping", "Executed Ping successfully", "graph TD\n  A --> B", List.of());
    doReturn(report).when(dslRuntime).explain(eq("Ping"), any());

    mockMvc
            .perform(
                    post("/api/dsl/explain/Ping")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"hello\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ping"))
            .andExpect(jsonPath("$.description").value("Executed Ping successfully"));
  }
}
