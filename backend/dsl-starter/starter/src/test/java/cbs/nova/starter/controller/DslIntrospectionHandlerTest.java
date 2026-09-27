package cbs.nova.starter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.starter.config.router.DslIntrospectionRouterConfiguration;
import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructPathType;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaMode;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.DslIntrospectionModels.LogicInfoDto;
import cbs.nova.starter.model.DslIntrospectionModels.LogicStatus;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectStructureDto;
import cbs.nova.starter.model.DslIntrospectionModels.StepDto;
import cbs.nova.starter.model.DslIntrospectionModels.StructureFieldDto;
import cbs.nova.starter.model.DslIntrospectionModels.WorkingSetResponse;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.service.DslIntrospectionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit characterization spec for {@link DslIntrospectionHandler}: the HTTP shell around a mocked
 * {@link DslIntrospectionService}, pinning status codes, the {@code Optional} to 404 mapping of the
 * {@code constructBody}/{@code constructSchema}/{@code objectStructure} routes, query-converter
 * pass-through for {@code searchObjects}, and pagination defaults/clamping.
 */
class DslIntrospectionHandlerTest {

  private DslIntrospectionService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(DslIntrospectionService.class);
    DslIntrospectionHandler handler = new DslIntrospectionHandler(service,
            new RequestQueryConverter());
    mockMvc = MockMvcBuilders
            .routerFunctions(
                    new DslIntrospectionRouterConfiguration().dslIntrospectionRouter(handler))
            .build();
  }

  @Test
  void searchObjectsPassesConvertedQueryThroughToService() throws Exception {
    when(service.searchObjects(anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 50));

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("page", "2")
            .param("size", "10")
            .param("query", "  Loan  ")
            .param("mode", "fuzzy")
            .param("type", "process")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0));

    verify(service).searchObjects(2, 10, "Loan", ObjectSearchMode.FUZZY, DslType.PROCESS);
  }

  @Test
  void searchObjectsAppliesDefaultPaginationAndExactMode() throws Exception {
    when(service.searchObjects(anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new PageResponse<>(List.of(sampleResult("Alpha")), 1, 0, 50));

    mockMvc.perform(get("/api/dsl/objects/search").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Alpha"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));

    verify(service).searchObjects(0, 50, null, ObjectSearchMode.EXACT, null);
  }

  @Test
  void searchObjectsClampsSizeAboveMaxLimit() throws Exception {
    when(service.searchObjects(anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 500));

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("size", "100000")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

    verify(service).searchObjects(0, 500, null, ObjectSearchMode.EXACT, null);
  }

  @Test
  void searchObjectsClampsNegativePageToZero() throws Exception {
    when(service.searchObjects(anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 50));

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("page", "-5")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

    verify(service).searchObjects(0, 50, null, ObjectSearchMode.EXACT, null);
  }

  @Test
  void searchObjectsTreatsBlankQueryAsNull() throws Exception {
    when(service.searchObjects(anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 50));

    mockMvc.perform(get("/api/dsl/objects/search")
            .param("query", "   ")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

    verify(service).searchObjects(eq(0), eq(50), isNull(), eq(ObjectSearchMode.EXACT), isNull());
  }

  @Test
  void workingSetReturnsAggregatedMetaPage() throws Exception {
    when(service.workingSet(any()))
            .thenReturn(new WorkingSetResponse(
                    List.of(meta("LoanA"), meta("LoanB"), meta("LoanC")),
                    3, 0, 2));

    mockMvc.perform(get("/api/dsl/working-set")
            .param("size", "2")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].name").value("LoanA"))
            .andExpect(jsonPath("$.items[0].status").value("Published"))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(2));
  }

  @Test
  void workingSetUsesDefaultLimitWhenSizeAbsent() throws Exception {
    when(service.workingSet(any()))
            .thenReturn(new WorkingSetResponse(List.of(), 0, 0, 50));

    mockMvc.perform(get("/api/dsl/working-set").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(50));

    verify(service).workingSet(any());
  }

  @Test
  void constructBodyReturns200WithBody() throws Exception {
    when(service.constructBody(ConstructPathType.PROCESSES, "LoanA"))
            .thenReturn(Optional.of(new ConstructBodyDto("LoanA", "process", "code()",
                    List.of(new StepDto("s1", "activity", "charge", null)))));

    mockMvc.perform(get("/api/dsl/processes/LoanA/construct").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanA"))
            .andExpect(jsonPath("$.type").value("process"))
            .andExpect(jsonPath("$.code").value("code()"))
            .andExpect(jsonPath("$.steps[0].id").value("s1"));
  }

  @Test
  void constructBodyReturns404WhenServiceHasNoMatch() throws Exception {
    when(service.constructBody(eq(ConstructPathType.PROCESSES), anyString()))
            .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/dsl/processes/Missing/construct")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
  }

  @Test
  void constructBodyReturns404ForUnknownPathTypeWithoutCallingService() throws Exception {
    mockMvc.perform(get("/api/dsl/workflows/LoanA/construct").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));

    verifyNoInteractions(service);
  }

  @Test
  void constructSchemaDefaultsToPreviewMode() throws Exception {
    when(service.constructSchema(ConstructPathType.HELPERS, "sampleHelper",
            ConstructSchemaMode.PREVIEW))
            .thenReturn(Optional.of(new ConstructSchemaDto("sampleHelper", "helper",
                    "java.lang.String", "java.lang.String", "desc",
                    Map.of("type", "object"), Map.of("type", "object"))));

    mockMvc.perform(get("/api/dsl/helpers/sampleHelper/schema").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("sampleHelper"))
            .andExpect(jsonPath("$.inputSchema.type").value("object"));
  }

  @Test
  void constructSchemaHonoursExplainMode() throws Exception {
    when(service.constructSchema(ConstructPathType.TRANSACTIONS, "SampleTx",
            ConstructSchemaMode.EXPLAIN))
            .thenReturn(Optional.of(new ConstructSchemaDto("SampleTx", "transaction",
                    "java.lang.String", "java.lang.String", "desc", null, null)));

    mockMvc.perform(get("/api/dsl/transactions/SampleTx/schema")
            .param("mode", "explain")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("SampleTx"))
            .andExpect(jsonPath("$.inputSchema").doesNotExist());
  }

  @Test
  void constructSchemaReturns404WhenServiceHasNoMatch() throws Exception {
    when(service.constructSchema(eq(ConstructPathType.FUNCTIONS), anyString(), any()))
            .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/dsl/functions/Missing/schema").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
  }

  @Test
  void constructSchemaReturns404ForUnknownPathTypeWithoutCallingService() throws Exception {
    mockMvc.perform(get("/api/dsl/workflows/LoanA/schema").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

    verifyNoInteractions(service);
  }

  @Test
  void objectStructureReturns200WithFields() throws Exception {
    when(service.objectStructure(ConstructPathType.PROCESSES, "LoanA"))
            .thenReturn(Optional.of(new ObjectStructureDto("LoanA", "process",
                    List.of(new StructureFieldDto("name", "LoanA", "scalar", "DSL name")),
                    List.of(
                            new LogicInfoDto("execute", LogicStatus.CONFIGURED, false, null)))));

    mockMvc.perform(get("/api/dsl/processes/LoanA/structure").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanA"))
            .andExpect(jsonPath("$.fields[0].path").value("name"))
            .andExpect(jsonPath("$.logic[0].kind").value("execute"))
            .andExpect(jsonPath("$.logic[0].status").value("configured"));
  }

  @Test
  void objectStructureReturns404WhenServiceHasNoMatch() throws Exception {
    when(service.objectStructure(eq(ConstructPathType.PROCESSES), anyString()))
            .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/dsl/processes/Missing/structure")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
  }

  @Test
  void objectStructureReturns404ForMismatchedType() throws Exception {
    when(service.objectStructure(eq(ConstructPathType.TRANSACTIONS), eq("LoanA")))
            .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/dsl/transactions/LoanA/structure")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

    verify(service).objectStructure(ConstructPathType.TRANSACTIONS, "LoanA");
  }

  private static ObjectSearchResult sampleResult(String name) {
    return new ObjectSearchResult(name, "process", "desc", "java.lang.String",
            "java.lang.String");
  }

  private static DefinitionMetaDto meta(String name) {
    return new DefinitionMetaDto(name, "process", "v1", "queue", "java.lang.String",
            "java.lang.String", false, "desc", null, DefinitionStatus.PUBLISHED, null);
  }
}
