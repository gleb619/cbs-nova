package cbs.nova.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.jsonschema.JacksonJsonSchemaGenerator;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.model.Descriptors;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.config.router.DslIntrospectionRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslIntrospectionHandler;
import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
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
            new JacksonJsonSchemaGenerator(),
            mapper,
            new DslDefinitionStatusResolver(DslProperties.builder().build(),
                    new DslGitStatusResolver(DslProperties.builder().build(), null)));
    DslIntrospectionHandler handler = new DslIntrospectionHandler(service,
            new HierarchyDiagramRenderer(), new RequestQueryConverter());
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
  void helpersEndpointReturnsEmptyPage() throws Exception {
    mockMvc
            .perform(get("/api/dsl/helpers").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(100));
  }

  @Test
  void helpersEndpointReturnsCatalogForRegisteredHelpers() throws Exception {
    registerSampleEntities();

    mockMvc
            .perform(get("/api/dsl/helpers").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(100))
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper')].description")
                    .value("A greeting helper"))
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper')].inputType").value("String"))
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper')].outputType").value("String"));
  }

  @Test
  void helpersEndpointPaginatesAndSearches() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/helpers")
            .param("limit", "1")
            .param("offset", "1")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(1))
            .andExpect(jsonPath("$.limit").value(1));

    mockMvc.perform(get("/api/dsl/helpers")
            .param("search", "sample")
            .param("searchMode", "exact")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[*].name")
                    .value(Matchers.hasItems("sampleHelper", "sampleFunction")));

    mockMvc.perform(get("/api/dsl/helpers")
            .param("search", "greeting")
            .param("searchMode", "exact")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("sampleHelper"));
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
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement' && @.type=='process')]")
                    .exists())
            .andExpect(jsonPath("$.items[?(@.name=='SampleTransaction' && @.type=='transaction')]")
                    .exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper' && @.type=='helper')]").exists())
            .andExpect(
                    jsonPath("$.items[?(@.name=='sampleFunction' && @.type=='function')]").exists())
            .andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void definitionsEndpointExposesInputSchemaForProcess() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement')].inputSchema").exists());
  }

  @Test
  void definitionsEndpointOmitsInputSchemaForHelper() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper')].inputSchema").doesNotExist())
            .andExpect(jsonPath("$.items[?(@.name=='sampleFunction')].inputSchema").doesNotExist())
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement')].inputSchema").exists());
  }

  @Test
  void constructSchemaEndpointReturnsInputAndOutputSchemasForProcess() throws Exception {
    mockMvc
            .perform(get("/api/dsl/schemas/LoanDisbursement").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDisbursement"))
            .andExpect(jsonPath("$.type").value("process"))
            .andExpect(jsonPath("$.inputSchema").exists())
            .andExpect(jsonPath("$.outputSchema").exists());
  }

  @Test
  void constructSchemaEndpointReturnsInputAndOutputSchemasForHelper() throws Exception {
    registerSampleEntities();

    mockMvc
            .perform(get("/api/dsl/schemas/sampleHelper").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("sampleHelper"))
            .andExpect(jsonPath("$.type").value("helper"))
            .andExpect(jsonPath("$.inputType").value("String"))
            .andExpect(jsonPath("$.outputType").value("String"))
            .andExpect(jsonPath("$.inputSchema").exists())
            .andExpect(jsonPath("$.outputSchema").exists());
  }

  @Test
  void constructSchemaEndpointReturnsInputAndOutputSchemasForFunction() throws Exception {
    registerSampleEntities();

    mockMvc
            .perform(get("/api/dsl/schemas/sampleFunction").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("sampleFunction"))
            .andExpect(jsonPath("$.type").value("function"))
            .andExpect(jsonPath("$.inputType").value("String"))
            .andExpect(jsonPath("$.outputType").value("String"))
            .andExpect(jsonPath("$.inputSchema").exists())
            .andExpect(jsonPath("$.outputSchema").exists());
  }

  @Test
  void constructSchemaEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/schemas/Unknown").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  @Test
  void constructSchemaEndpointReturnsSchemasForExplainMode() throws Exception {
    mockMvc
            .perform(get("/api/dsl/schemas/LoanDisbursement")
                    .param("mode", "explain")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDisbursement"))
            .andExpect(jsonPath("$.type").value("process"))
            .andExpect(jsonPath("$.inputSchema").exists())
            .andExpect(jsonPath("$.outputType").value("ExplainReport"))
            .andExpect(jsonPath("$.outputSchema.type").value("object"))
            .andExpect(jsonPath("$.outputSchema.properties.name.type").value("string"))
            .andExpect(jsonPath("$.outputSchema.properties.description.type").value("string"))
            .andExpect(jsonPath("$.outputSchema.properties.markdown.type").value("string"))
            .andExpect(jsonPath("$.outputSchema.properties.children.type").value("array"))
            .andExpect(jsonPath("$.outputSchema.properties.children.items['$ref']")
                    .value("#/$defs/ExplainReport"));
  }

  @Test
  void constructSchemaEndpointReturnsSchemasForFunctionInExplainMode() throws Exception {
    registerSampleEntities();

    mockMvc
            .perform(get("/api/dsl/schemas/sampleFunction")
                    .param("mode", "explain")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("sampleFunction"))
            .andExpect(jsonPath("$.type").value("function"))
            .andExpect(jsonPath("$.inputType").value("String"))
            .andExpect(jsonPath("$.inputSchema").exists())
            .andExpect(jsonPath("$.outputType").value("ExplainReport"))
            .andExpect(jsonPath("$.outputSchema.properties.children.items['$ref']")
                    .value("#/$defs/ExplainReport"));
  }

  @Test
  void constructSchemaEndpointDefaultsToPreviewSchemasWithoutMode() throws Exception {
    mockMvc
            .perform(get("/api/dsl/schemas/LoanDisbursement").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("process"))
            .andExpect(jsonPath("$.inputSchema").exists());
  }

  @Test
  void constructSchemaEndpointReturns404ForUnknownInExplainMode() throws Exception {
    mockMvc
            .perform(get("/api/dsl/schemas/Unknown")
                    .param("mode", "explain")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  @Test
  void definitionsEndpointReturnsOnlyTheSetupProcessWhenNoSamplesRegistered() throws Exception {
    mockMvc.perform(get("/api/dsl/definitions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement' && @.type=='process')]")
                    .exists())
            .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void workingSetEndpointReturnsAllEntitiesWithoutFilters() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/working-set").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement' && @.type=='process')]")
                    .exists())
            .andExpect(jsonPath("$.items[?(@.name=='SampleTransaction' && @.type=='transaction')]")
                    .exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper' && @.type=='helper')]")
                    .exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleFunction' && @.type=='function')]")
                    .exists())
            .andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void workingSetEndpointFiltersByName() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/working-set")
            .param("name", "sample")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items[?(@.name=='SampleTransaction')]").exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper')]").exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement')]").doesNotExist());
  }

  @Test
  void workingSetEndpointFiltersByType() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/working-set")
            .param("type", "helper")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("sampleHelper"));
  }

  @Test
  void workingSetEndpointFiltersByDescription() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/working-set")
            .param("description", "greeting")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(2));
  }

  @Test
  void workingSetEndpointCombinesFiltersAndPaginates() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/working-set")
            .param("name", "sample")
            .param("type", "function")
            .param("limit", "1")
            .param("offset", "0")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("sampleFunction"))
            .andExpect(jsonPath("$.limit").value(1))
            .andExpect(jsonPath("$.offset").value(0));
  }

  @Test
  void workingSetEndpointPaginatesWithOffset() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/working-set")
            .param("name", "sample")
            .param("limit", "1")
            .param("offset", "1")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.offset").value(1))
            .andExpect(jsonPath("$.limit").value(1));
  }

  @Test
  void workingSetEndpointDefaultsLimitAndOffset() throws Exception {
    mockMvc.perform(get("/api/dsl/working-set").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(50))
            .andExpect(jsonPath("$.offset").value(0));
  }

  @Test
  void structuresEndpointReturnsFlatFieldsForProcess() throws Exception {
    mockMvc
            .perform(get("/api/dsl/structures/LoanDisbursement")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDisbursement"))
            .andExpect(jsonPath("$.type").value("process"))
            .andExpect(jsonPath("$.fields").isArray())
            .andExpect(jsonPath("$.fields[?(@.path=='name')].value").value("LoanDisbursement"))
            .andExpect(jsonPath("$.fields[?(@.path=='version')].value").value("v1"))
            .andExpect(jsonPath("$.fields[?(@.path=='taskQueue')].value").isNotEmpty())
            .andExpect(jsonPath("$.fields[?(@.path=='hasCompensation')].value")
                    .value("false"))
            .andExpect(jsonPath("$.fields[?(@.path=='taskQueue')].description")
                    .value("Temporal task queue this workflow polls"));
  }

  @Test
  void structuresEndpointReportsDefaultLogicForProcess() throws Exception {
    mockMvc
            .perform(get("/api/dsl/structures/LoanDisbursement")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logic[?(@.kind=='execute')].status").value("configured"))
            .andExpect(jsonPath("$.logic[?(@.kind=='execute')].required").value(true))
            .andExpect(jsonPath("$.logic[?(@.kind=='preview')].status").value("default"))
            .andExpect(jsonPath("$.logic[?(@.kind=='explain')].status").value("default"));
  }

  @Test
  void structuresEndpointReportsConfiguredLogicForProcess() throws Exception {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("ConfiguredLogicProcess")
                    .execute(ctx -> Result.success("ok"))
                    .preview(ctx -> Result.success("preview"))
                    .explain(ctx -> Result.success(ExplainReport.of("report")))
                    .build());

    mockMvc
            .perform(get("/api/dsl/structures/ConfiguredLogicProcess")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logic[?(@.kind=='execute')].status").value("configured"))
            .andExpect(jsonPath("$.logic[?(@.kind=='preview')].status").value("configured"))
            .andExpect(jsonPath("$.logic[?(@.kind=='explain')].status").value("configured"));
  }

  @Test
  void structuresEndpointReturnsFieldsForRegisteredHelper() throws Exception {
    registerSampleEntities();

    mockMvc
            .perform(get("/api/dsl/structures/sampleHelper").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("sampleHelper"))
            .andExpect(jsonPath("$.type").value("helper"))
            .andExpect(jsonPath("$.fields[?(@.path=='inputType')].value").value("String"))
            .andExpect(jsonPath("$.fields[?(@.path=='inputType')].type").value("class"))
            .andExpect(jsonPath("$.logic[?(@.kind=='execute')].status").value("configured"))
            .andExpect(jsonPath("$.logic[?(@.kind=='preview')].status").value("default"));
  }

  @Test
  void structuresEndpointReturns404ForUnknown() throws Exception {
    mockMvc
            .perform(get("/api/dsl/structures/Unknown").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }

  private void registerSampleEntities() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("SampleTransaction")
                    .execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerHelper("sampleHelper", new SampleHelper());
    var sampleFunction = FunctionDslObject.builder()
            .name("sampleFunction")
            .executeLogic(ctx -> Result.success("ok"))
            .descriptor(Descriptors.from("sampleFunction",
                    new ExecutableDescriptor(
                            "sampleFunction",
                            "A greeting function",
                            String.class,
                            String.class,
                            List.of())))
            .build();
    GlobalManager.globalManager().registerFunction(sampleFunction);
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
              List.of());
    }
  }
}
