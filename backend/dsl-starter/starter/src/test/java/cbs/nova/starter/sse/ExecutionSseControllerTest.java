package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExecutionSseControllerTest {

  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
  private final ExecutionSseService sseService = new ExecutionSseService();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ExecutionSseController controller = new ExecutionSseController(sseService, repository);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void unknownRunReturns404() throws Exception {
    mockMvc.perform(get("/api/executions/missing/events")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isNotFound());
  }

  @Test
  void knownRunOpensSseStreamAndSendsConnectedComment() throws Exception {
    repository.save(DslRun.builder()
            .runId("run-live")
            .processName("Loan")
            .status("RUNNING")
            .startedAt(Instant.now())
            .executionMode("RUN")
            .build());

    mockMvc.perform(get("/api/executions/run-live/events")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(content().string(containsString(":connected")));

    assertThat(sseService.subscriberCount("run-live")).isEqualTo(1);
  }

}
