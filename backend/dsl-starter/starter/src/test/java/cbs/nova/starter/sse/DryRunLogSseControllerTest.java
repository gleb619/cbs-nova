package cbs.nova.starter.sse;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DryRunLogSseControllerTest {

  private final DryRunLogSseService sseService = new DryRunLogSseService();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DryRunLogSseController controller = new DryRunLogSseController(sseService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void malformedTraceIdReturns404() throws Exception {
    mockMvc.perform(get("/api/dsl/dry-run/bad!trace/logs")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isNotFound());

    Assertions.assertThat(sseService.subscriberCount("bad!trace")).isEqualTo(0);
  }

  @Test
  void validTraceIdOpensSseStreamAndSendsConnectedComment() throws Exception {
    mockMvc.perform(get("/api/dsl/dry-run/trace-1/logs")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(content().string(containsString(":connected")));

    Assertions.assertThat(sseService.subscriberCount("trace-1")).isEqualTo(1);
  }
}
