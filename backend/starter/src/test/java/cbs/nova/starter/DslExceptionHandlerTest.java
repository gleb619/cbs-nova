package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.DslException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class DslExceptionHandlerTest {

  @Test
  void generalExceptionMapsTo500WithInternalErrorCode() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/general"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("boom"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void illegalArgumentExceptionMapsTo400WithBadRequestCode() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/bad"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("bad arg"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void dslExceptionMapsTo422WithStructuredFields() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/dsl"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("EXECUTION_FAILED"))
            .andExpect(jsonPath("$.message").value("dsl failed"))
            .andExpect(jsonPath("$.runId").value("run-abc"))
            .andExpect(jsonPath("$.exceptionId").isString());
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/throw/general")
    String throwGeneral() {
      throw new RuntimeException("boom");
    }

    @GetMapping("/throw/bad")
    String throwBad() {
      throw new IllegalArgumentException("bad arg");
    }

    @GetMapping("/throw/dsl")
    String throwDsl() {
      throw new DslException("run-abc", DslErrorCode.EXECUTION_FAILED, "dsl failed");
    }
  }
}
