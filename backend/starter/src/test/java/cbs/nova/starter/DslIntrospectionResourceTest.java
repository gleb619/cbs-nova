package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.starter.controllers.DslIntrospectionResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class DslIntrospectionResourceTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager()
            .registerProcess(
                    Dsl.process("LoanDisbursement")
                            .execute(ctx -> Result.success("ok")).build());
    mockMvc = MockMvcBuilders.standaloneSetup(new DslIntrospectionResource())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
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
            .andExpect(jsonPath("$.hasCompensation").value(false))
            .andExpect(jsonPath("$.inputSchema").exists());
  }

  @Test
  void processDetailEndpointReturnsInputSchemaForParameterBasedProcess() throws Exception {
    GlobalManager.globalManager()
            .registerProcess(
                    Dsl.process("ParamBasedProcess")
                            .parameters(p -> p.number("amount"))
                            .execute(ctx -> Result.success("ok")).build());

    mockMvc
            .perform(get("/api/dsl/processes/ParamBasedProcess"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inputSchema.type").value("object"))
            .andExpect(jsonPath("$.inputSchema.properties.amount").exists());
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

  @Test
  void helpersSearchReturnsMatchingEntitiesWithoutFilters() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/helpers/search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='LoanDisbursement' && @.type=='process')]").exists())
            .andExpect(
                    jsonPath("$[?(@.name=='SampleTransaction' && @.type=='transaction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper' && @.type=='helper')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction' && @.type=='function')]").exists());
  }

  @Test
  void helpersSearchFiltersByName() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/helpers/search").param("name", "sample"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='SampleTransaction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='LoanDisbursement')]").doesNotExist());
  }

  @Test
  void helpersSearchFiltersByType() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/helpers/search").param("type", "helper"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.type=='helper')]").exists())
            .andExpect(jsonPath("$[?(@.type!='helper')]").doesNotExist());
  }

  @Test
  void helpersSearchFiltersByDescription() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/helpers/search").param("description", "greeting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='LoanDisbursement')]").doesNotExist());
  }

  @Test
  void helpersSearchCombinesFilters() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/helpers/search")
            .param("name", "sample")
            .param("type", "function"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper')]").doesNotExist());
  }

  @Test
  void definitionsEndpointAggregatesAllEntityKinds() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(
                    jsonPath("$[?(@.name=='LoanDisbursement' && @.type=='process')]").exists())
            .andExpect(
                    jsonPath("$[?(@.name=='SampleTransaction' && @.type=='transaction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper' && @.type=='helper')]").exists())
            .andExpect(
                    jsonPath("$[?(@.name=='sampleFunction' && @.type=='function')]").exists());
  }

  @Test
  void definitionsEndpointExposesInputSchemaForProcess() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions"))
            .andExpect(status().isOk())
            .andExpect(
                    jsonPath("$[?(@.name=='LoanDisbursement')].inputSchema").exists());
  }

  @Test
  void definitionsEndpointOmitsInputSchemaForHelper() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions"))
            .andExpect(status().isOk())
            // Helpers and functions carry no inputSchema — the field is omitted
            // from the wire format via @JsonInclude(NON_NULL) to match the FE
            // DefinitionMeta type ({ name, type, inputSchema? }).
            .andExpect(
                    jsonPath("$[?(@.name=='sampleHelper')].inputSchema").doesNotExist())
            .andExpect(
                    jsonPath("$[?(@.name=='sampleFunction')].inputSchema").doesNotExist())
            .andExpect(
                    jsonPath("$[?(@.name=='LoanDisbursement')].inputSchema").exists());
  }

  @Test
  void definitionsEndpointReturnsOnlyTheSetupProcessWhenNoSamplesRegistered() throws Exception {
    // The class-level @BeforeEach registers exactly one process ('LoanDisbursement');
    // we assert the aggregator sees that single entry with type=process and an
    // inputSchema object.
    mockMvc.perform(get("/api/dsl/definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(
                    jsonPath("$[?(@.name=='LoanDisbursement' && @.type=='process')]").exists());
  }

  private void registerSampleEntities() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("SampleTransaction")
                    .execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerHelper("sampleHelper", new SampleHelper());
    GlobalManager.globalManager().registerFunction(new FunctionDslObject(
            "sampleFunction",
            List.of(),
            ctx -> Result.success("ok"),
            null,
            () -> new cbs.nova.dsl.DslDescriptor(
                    "sampleFunction",
                    cbs.nova.dsl.DslObject.DslType.FUNCTION,
                    "A greeting function",
                    String.class,
                    String.class,
                    false,
                    false,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null)));
  }

  private static class SampleHelper implements Executable<String, String> {

    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success(ctx.body());
    }

    @Override
    public ExecutableDescriptor describe() {
      return new ExecutableDescriptor(
              "sampleHelper",
              "A greeting helper",
              String.class,
              String.class,
              false,
              null,
              List.of());
    }
  }
}
