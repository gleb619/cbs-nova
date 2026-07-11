package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DslIntrospectionResourceTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    GlobalManager.getInstance().resetForTests();
    GlobalManager.getInstance()
            .registerProcess(
                    Dsl.process("LoanDisbursement")
                            .execute(ctx -> Result.success("ok")).build());
    mockMvc = MockMvcBuilders.standaloneSetup(new DslIntrospectionResource())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void processesEndpointReturnsRegisteredNames() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names[0]").value("LoanDisbursement"));
  }

  @Test
  void transactionsEndpointReturnsEmptyList() throws Exception {
    mockMvc
            .perform(get("/api/dsl/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
  }

  @Test
  void helpersEndpointReturnsEmptyList() throws Exception {
    mockMvc
            .perform(get("/api/dsl/helpers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
  }

  @Test
  void processDetailEndpointReturnsDetails() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/LoanDisbursement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDisbursement"))
            .andExpect(jsonPath("$.version").value("v1"))
            .andExpect(jsonPath("$.hasCompensation").value(false));
  }

  @Test
  void processDetailEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/Unknown"))
            .andExpect(status().isNotFound());
  }

  @Test
  void transactionDetailEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/transactions/Unknown"))
            .andExpect(status().isNotFound());
  }
}
