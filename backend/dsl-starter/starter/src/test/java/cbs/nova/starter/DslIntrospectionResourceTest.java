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
import cbs.nova.starter.service.DslDefinitionStatusResolver;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.converter.DslIntrospectionMapper;
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
            new RequestQueryConverter());
    DslIntrospectionRouterConfiguration router = new DslIntrospectionRouterConfiguration();
    mockMvc = MockMvcBuilders.routerFunctions(router.dslIntrospectionRouter(handler)).build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void objectsSearchReturnsEmptyPageWhenNothingRegistered() throws Exception {
    GlobalManager.globalManager().resetForTests();

    mockMvc.perform(get("/api/dsl/objects/search").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void objectsSearchReturnsAllEntitiesWithoutFilters() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement' && @.type=='process')]")
                    .exists())
            .andExpect(
                    jsonPath("$.items[?(@.name=='SampleTransaction' && @.type=='transaction')]")
                            .exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper' && @.type=='helper')]")
                    .exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleFunction' && @.type=='function')]")
                    .exists())
            .andExpect(jsonPath("$.total").value(4));
  }

  @Test
  void objectsSearchFiltersByQuery() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("query", "sample")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[?(@.name=='SampleTransaction')]").exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleHelper')]").exists())
            .andExpect(jsonPath("$.items[?(@.name=='sampleFunction')]").exists())
            .andExpect(jsonPath("$.items[?(@.name=='LoanDisbursement')]").doesNotExist())
            .andExpect(jsonPath("$.total").value(3));
  }

  @Test
  void objectsSearchPaginates() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("query", "sample")
            .param("page", "1")
            .param("size", "1")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.offset").value(1))
            .andExpect(jsonPath("$.limit").value(1));
  }

  @Test
  void objectsSearchDefaultsPageAndSize() throws Exception {
    registerSampleEntities();

    mockMvc.perform(get("/api/dsl/objects/search").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(50))
            .andExpect(jsonPath("$.offset").value(0));
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
