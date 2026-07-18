package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.controllers.DslReloadResource;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DslReloadResourceTest {

  private DslReloadResource resource;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    GlobalManager.globalManager().resetForTests();
    resource = new DslReloadResource();
    mockMvc = MockMvcBuilders.standaloneSetup(resource)
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  private void setSourceDir(String value) throws Exception {
    Field field = DslReloadResource.class.getDeclaredField("sourceDirProperty");
    field.setAccessible(true);
    field.set(resource, value);
  }

  @Test
  void reloadReturns409WhenSourceDirBlank() throws Exception {
    setSourceDir("");

    mockMvc
            .perform(post("/api/dsl/reload"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOT_CONFIGURED"));
  }

  @Test
  void reloadReturns409WhenSourceDirNotFound() throws Exception {
    setSourceDir("/tmp/cbs-nova-does-not-exist-" + System.nanoTime());

    mockMvc
            .perform(post("/api/dsl/reload"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
