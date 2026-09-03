package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.starter.config.router.DslIntrospectionRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslIntrospectionHandler;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import cbs.nova.starter.service.DslDefinitionStatusResolver;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
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
    DslIntrospectionMapper mapper = Mappers.getMapper(DslIntrospectionMapper.class);
    DslIntrospectionService service = new DslIntrospectionService(
            DslConfig.dslConfig().jsonSchemaGenerator().get(),
            mapper,
            new DslDefinitionStatusResolver(
                    new DslProperties(),
                    new DslGitStatusResolver(
                            new DslProperties())));
    DslIntrospectionHandler handler = new DslIntrospectionHandler(service,
            new ExplainDiagramRenderer());
    DslIntrospectionRouterConfiguration router = new DslIntrospectionRouterConfiguration();
    mockMvc = MockMvcBuilders.routerFunctions(router.dslIntrospectionRouter(handler)).build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void processesEndpointReturnsRegisteredNames() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names[0]").value("LoanDisbursement"));
  }

  @Test
  void transactionsEndpointReturnsEmptyList() throws Exception {
    mockMvc
            .perform(get("/api/dsl/transactions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
  }

  @Test
  void helpersEndpointReturnsEmptyList() throws Exception {
    mockMvc
            .perform(get("/api/dsl/helpers").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray())
            .andExpect(jsonPath("$.helpers").isArray());
  }

  @Test
  void helpersEndpointReturnsCatalogForRegisteredHelpers() throws Exception {
    registerSampleEntities();

    mockMvc
            .perform(get("/api/dsl/helpers").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray())
            .andExpect(jsonPath("$.names").value(Matchers.hasItem("sampleHelper")))
            .andExpect(jsonPath("$.helpers").isArray())
            .andExpect(jsonPath("$.helpers[?(@.name=='sampleHelper')].description")
                    .value("A greeting helper"))
            .andExpect(jsonPath("$.helpers[?(@.name=='sampleHelper')].inputType").value("String"))
            .andExpect(jsonPath("$.helpers[?(@.name=='sampleHelper')].outputType").value("String"))
            .andExpect(jsonPath("$.helpers[?(@.name=='sampleHelper')].hasSideEffects").value(false))
            .andExpect(jsonPath("$.helpers[?(@.name=='sampleHelper')].previewBehavior")
                    .doesNotExist());
  }

  @Test
  void processDetailEndpointReturnsDetails() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/LoanDisbursement").accept(MediaType.APPLICATION_JSON))
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
            .perform(get("/api/dsl/processes/ParamBasedProcess").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inputSchema.type").value("object"))
            .andExpect(jsonPath("$.inputSchema.properties.amount").exists());
  }

  @Test
  void processDetailEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/Unknown").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  @Test
  void processDiagramEndpointReturnsMermaidForKnownProcess() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/LoanDisbursement/diagram")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDisbursement"))
            .andExpect(jsonPath("$.format").value("mermaid"))
            .andExpect(jsonPath("$.diagram").isNotEmpty());
  }

  @Test
  void processDiagramEndpointHonoursFormatQueryParam() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/LoanDisbursement/diagram")
                    .param("format", "bpmn")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.format").value("bpmn"))
            .andExpect(jsonPath("$.diagram").isNotEmpty());
  }

  @Test
  void processDiagramEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/processes/Unknown/diagram")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  @Test
  void transactionDetailEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/transactions/Unknown").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  @Test
  void helpersSearchReturnsMatchingEntitiesWithoutFilters() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='LoanDisbursement' && @.type=='process')]").exists())
            .andExpect(
                    jsonPath("$[?(@.name=='SampleTransaction' && @.type=='transaction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper' && @.type=='helper')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction' && @.type=='function')]").exists());
  }

  @Test
  void objectsSearchFiltersByName() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("name", "sample")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='SampleTransaction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='LoanDisbursement')]").doesNotExist());
  }

  @Test
  void objectsSearchFiltersByType() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("type", "helper")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.type=='helper')]").exists())
            .andExpect(jsonPath("$[?(@.type!='helper')]").doesNotExist());
  }

  @Test
  void objectsSearchFiltersByDescription() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("description", "greeting")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='LoanDisbursement')]").doesNotExist());
  }

  @Test
  void objectsSearchCombinesFilters() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("name", "sample")
            .param("type", "function")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$[?(@.name=='sampleHelper')]").doesNotExist());
  }

  @Test
  void definitionsEndpointAggregatesAllEntityKinds() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
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

    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(
                    jsonPath("$[?(@.name=='LoanDisbursement')].inputSchema").exists());
  }

  @Test
  void definitionsEndpointOmitsInputSchemaForHelper() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(
                    jsonPath("$[?(@.name=='sampleHelper')].inputSchema").doesNotExist())
            .andExpect(
                    jsonPath("$[?(@.name=='sampleFunction')].inputSchema").doesNotExist())
            .andExpect(
                    jsonPath("$[?(@.name=='LoanDisbursement')].inputSchema").exists());
  }

  @Test
  void definitionsEndpointReturnsOnlyTheSetupProcessWhenNoSamplesRegistered() throws Exception {
    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
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
            () -> new DslDescriptor(
                    "sampleFunction",
                    DslType.FUNCTION,
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
