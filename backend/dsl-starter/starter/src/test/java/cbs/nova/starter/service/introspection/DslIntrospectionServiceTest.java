package cbs.nova.starter.service.introspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.helper.HelperSource;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructPathType;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.jsonschema.JacksonJsonSchemaGenerator;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.RequestQueryModels.ObjectSearchQuery;
import cbs.nova.starter.model.DslIntrospectionModels.LogicInfoDto;
import cbs.nova.starter.model.DslIntrospectionModels.LogicStatus;
import cbs.nova.starter.service.DslDefinitionStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslIntrospectionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DslIntrospectionServiceTest {

  private DslIntrospectionService service;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    DslIntrospectionMapper mapper = Mappers.getMapper(DslIntrospectionMapper.class);
    service = new DslIntrospectionService(
            new JacksonJsonSchemaGenerator(),
            mapper,
            new DslDefinitionStatusResolver(DslProperties.builder().build(),
                    new DslGitStatusResolver(DslProperties.builder().build(), null),
                    new DslSourcePathResolver(DslProperties.builder().build())));
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void searchObjectsReturnsAllEntityKinds() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("P1").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("T1").execute(ctx -> Result.success("ok")).build());
    registerSampleHelper("H1", "helper desc", String.class, Integer.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(0, 50, null,
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.ALL, null);

    assertThat(page.items()).extracting("name", "type")
            .contains(tuple("P1", "process"), tuple("T1", "transaction"),
                    tuple("H1", "helper"));
    assertThat(page.total()).isEqualTo(3);
  }

  @Test
  void workingSetMapsProcessAndTransaction() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("P2")
                    .version("v1")
                    .taskQueue("q1")
                    .input(String.class)
                    .output(Integer.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("T2")
                    .version("v2")
                    .taskQueue("q2")
                    .input(Long.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var definitions = service.workingSet(new ObjectSearchQuery(0, 500, null, null, null)).items();

    assertThat(definitions).extracting("name").contains("P2", "T2");
    assertThat(definitions)
            .anySatisfy(d -> {
              assertThat(d.name()).isEqualTo("P2");
              assertThat(d.type()).isEqualTo("process");
              assertThat(d.version()).isEqualTo("v1");
              assertThat(d.taskQueue()).isEqualTo("q1");
              assertThat(d.inputType()).isEqualTo("String");
              assertThat(d.outputType()).isEqualTo("Integer");
              assertThat(d.hasCompensation()).isFalse();
              assertThat(d.inputSchema()).isNotNull();
              assertThat(d.status()).isEqualTo(DefinitionStatus.PUBLISHED);
            });
    assertThat(definitions)
            .anySatisfy(d -> {
              assertThat(d.name()).isEqualTo("T2");
              assertThat(d.type()).isEqualTo("transaction");
              assertThat(d.version()).isEqualTo("v2");
              assertThat(d.taskQueue()).isEqualTo("q2");
              assertThat(d.inputType()).isEqualTo("Long");
              assertThat(d.outputType()).isEqualTo("String");
              assertThat(d.hasCompensation()).isFalse();
              assertThat(d.inputSchema()).isNotNull();
              assertThat(d.status()).isEqualTo(DefinitionStatus.PUBLISHED);
            });
  }

  @Test
  void constructSchemaReturnsInputAndOutputForProcess() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PSchema")
                    .input(String.class)
                    .output(Integer.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = (ConstructSchemaDto) service
            .constructSchema(ConstructPathType.PROCESSES, "PSchema", ConstructSchemaMode.PREVIEW)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("process");
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.outputType()).isEqualTo("Integer");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.outputSchema()).isNotNull();
  }

  @Test
  void constructSchemaReturnsInputAndOutputForTransaction() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("TSchema")
                    .input(Long.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = (ConstructSchemaDto) service
            .constructSchema(ConstructPathType.TRANSACTIONS, "TSchema", ConstructSchemaMode.PREVIEW)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("transaction");
    assertThat(dto.inputType()).isEqualTo("Long");
    assertThat(dto.outputType()).isEqualTo("String");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.outputSchema()).isNotNull();
  }

  @Test
  void constructSchemaReturnsInputAndOutputForHelper() {
    GlobalManager.globalManager().registerHelper("HSchema", new Executable<String, Integer>() {
      @Override
      public Result<Integer> execute(Context<String> ctx) {
        return Result.success(1);
      }

      @Override
      public ExecutableDescriptor describe() {
        return new ExecutableDescriptor(
                "HSchema",
                "A helper",
                String.class,
                Integer.class,
                List.of());
      }
    });

    var dto = (ConstructSchemaDto) service
            .constructSchema(ConstructPathType.HELPERS, "HSchema", ConstructSchemaMode.PREVIEW)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("helper");
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.outputType()).isEqualTo("Integer");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.outputSchema()).isNotNull();
  }

  @Test
  void constructSchemaReturnsInputSchemaForParameterBasedFunction() {
    GlobalManager.globalManager().registerFunction(
            Dsl.function("FSchema")
                    .parameters(p -> p.string("greeting"))
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = (ConstructSchemaDto) service
            .constructSchema(ConstructPathType.FUNCTIONS, "FSchema", ConstructSchemaMode.PREVIEW)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("function");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.inputSchema()).containsKey("properties");
    assertThat((Map<String, Object>) dto.inputSchema().get("properties"))
            .containsKey("greeting");
  }

  @Test
  void constructSchemaReturnsEmptyForUnknown() {
    assertThat(service.constructSchema(ConstructPathType.PROCESSES, "NoSuchConstruct",
            ConstructSchemaMode.PREVIEW)).isEmpty();
    assertThat(service.constructSchema(ConstructPathType.PROCESSES, "NoSuchConstruct",
            ConstructSchemaMode.EXPLAIN)).isEmpty();
  }

  @Test
  void constructPathTypeParsesAllowedSegmentsCaseInsensitively() {
    assertThat(ConstructPathType.from("processes")).contains(ConstructPathType.PROCESSES);
    assertThat(ConstructPathType.from("Transactions")).contains(ConstructPathType.TRANSACTIONS);
    assertThat(ConstructPathType.from("FUNCTIONS")).contains(ConstructPathType.FUNCTIONS);
    assertThat(ConstructPathType.from(" helpers ")).contains(ConstructPathType.HELPERS);
    assertThat(ConstructPathType.from("workflows")).isEmpty();
    assertThat(ConstructPathType.from("")).isEmpty();
    assertThat(ConstructPathType.from(null)).isEmpty();
  }

  @Test
  void constructBodyResolvesOnlyWithinRequestedType() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PBody").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("TBody").execute(ctx -> Result.success("ok")).build());

    assertThat(service.constructBody(ConstructPathType.PROCESSES, "PBody"))
            .hasValueSatisfying(dto -> assertThat(dto.type()).isEqualTo("process"));
    assertThat(service.constructBody(ConstructPathType.TRANSACTIONS, "TBody"))
            .hasValueSatisfying(dto -> assertThat(dto.type()).isEqualTo("transaction"));
    assertThat(service.constructBody(ConstructPathType.TRANSACTIONS, "PBody")).isEmpty();
    assertThat(service.constructBody(ConstructPathType.FUNCTIONS, "PBody")).isEmpty();
    assertThat(service.constructBody(ConstructPathType.HELPERS, "PBody")).isEmpty();
  }

  @Test
  void constructSchemaResolvesOnlyWithinRequestedType() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PScoped").execute(ctx -> Result.success("ok")).build());

    assertThat(service.constructSchema(ConstructPathType.PROCESSES, "PScoped",
            ConstructSchemaMode.PREVIEW)).isPresent();
    assertThat(service.constructSchema(ConstructPathType.TRANSACTIONS, "PScoped",
            ConstructSchemaMode.PREVIEW)).isEmpty();
    assertThat(service.constructSchema(ConstructPathType.HELPERS, "PScoped",
            ConstructSchemaMode.EXPLAIN)).isEmpty();
  }

  @Test
  void objectStructureResolvesOnlyWithinRequestedType() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PScopedStructure").execute(ctx -> Result.success("ok")).build());

    assertThat(service.objectStructure(ConstructPathType.PROCESSES, "PScopedStructure"))
            .isPresent();
    assertThat(service.objectStructure(ConstructPathType.TRANSACTIONS, "PScopedStructure"))
            .isEmpty();
    assertThat(service.objectStructure(ConstructPathType.FUNCTIONS, "PScopedStructure")).isEmpty();
  }

  @Test
  void constructSchemaInExplainModeReturnsSchemaForProcess() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PExplain")
                    .input(String.class)
                    .output(Integer.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = service
            .constructSchema(ConstructPathType.PROCESSES, "PExplain", ConstructSchemaMode.EXPLAIN)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("process");
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.outputSchema()).isNotNull();
    assertExplainOutputSchema(dto);
  }

  @Test
  void constructSchemaInExplainModeReturnsSchemaForTransaction() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("TExplain")
                    .input(Long.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = service.constructSchema(ConstructPathType.TRANSACTIONS, "TExplain",
            ConstructSchemaMode.EXPLAIN).orElseThrow();

    assertThat(dto.type()).isEqualTo("transaction");
    assertThat(dto.inputType()).isEqualTo("Long");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.outputSchema()).isNotNull();
    assertExplainOutputSchema(dto);
  }

  @Test
  void constructSchemaInExplainModeReturnsSchemaForHelper() {
    GlobalManager.globalManager().registerHelper("HExplain", new Executable<String, Integer>() {
      @Override
      public Result<Integer> execute(Context<String> ctx) {
        return Result.success(1);
      }

      @Override
      public ExecutableDescriptor describe() {
        return new ExecutableDescriptor(
                "HExplain", "A helper", String.class, Integer.class, List.of());
      }
    });

    var dto = service
            .constructSchema(ConstructPathType.HELPERS, "HExplain", ConstructSchemaMode.EXPLAIN)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("helper");
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.outputSchema()).isNotNull();
    assertExplainOutputSchema(dto);
  }

  @Test
  void constructSchemaInExplainModeReturnsSchemaForFunction() {
    GlobalManager.globalManager().registerFunction(
            Dsl.function("FExplain")
                    .parameters(p -> p.string("greeting"))
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = service
            .constructSchema(ConstructPathType.FUNCTIONS, "FExplain", ConstructSchemaMode.EXPLAIN)
            .orElseThrow();

    assertThat(dto.type()).isEqualTo("function");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.inputSchema()).containsKey("properties");
    assertThat((Map<String, Object>) dto.inputSchema().get("properties"))
            .containsKey("greeting");
    assertExplainOutputSchema(dto);
  }

  private static void assertExplainOutputSchema(ConstructSchemaDto dto) {
    assertThat(dto.outputType()).isEqualTo("ExplainReport");
    assertThat(dto.outputSchema()).containsEntry("type", "object");
    assertThat((Map<String, Object>) dto.outputSchema().get("properties"))
            .containsKeys("name", "description", "markdown", "children");
    Map<String, Object> children = (Map<String, Object>) ((Map<String, Object>) dto
            .outputSchema().get("properties")).get("children");
    assertThat(children).containsEntry("type", "array");
    assertThat((Map<String, Object>) children.get("items"))
            .containsEntry("$ref", "#/$defs/ExplainReport");
  }

  @Test
  void objectStructureReportsDefaultLogicForBareProcess() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PLogic").execute(ctx -> Result.success("ok")).build());

    var dto = service.objectStructure(ConstructPathType.PROCESSES, "PLogic").orElseThrow();

    assertThat(dto.logic())
            .extracting(LogicInfoDto::kind, LogicInfoDto::status, LogicInfoDto::required)
            .containsExactly(
                    tuple("execute", LogicStatus.CONFIGURED, true),
                    tuple("preview", LogicStatus.DEFAULT, false),
                    tuple("explain", LogicStatus.DEFAULT, false));
  }

  @Test
  void objectStructureReportsConfiguredLogicWhenPreviewAndExplainAreSet() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PLogicFull")
                    .execute(ctx -> Result.success("ok"))
                    .preview(ctx -> Result.success("preview"))
                    .explain(ctx -> Result.success(ExplainReport.of("report")))
                    .build());

    var dto = service.objectStructure(ConstructPathType.PROCESSES, "PLogicFull").orElseThrow();

    assertThat(dto.logic())
            .extracting(LogicInfoDto::kind, LogicInfoDto::status, LogicInfoDto::required)
            .containsExactly(
                    tuple("execute", LogicStatus.CONFIGURED, true),
                    tuple("preview", LogicStatus.CONFIGURED, false),
                    tuple("explain", LogicStatus.CONFIGURED, false));
  }

  @Test
  void objectStructureReportsDefaultLogicForTransaction() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("TLogic").execute(ctx -> Result.success("ok")).build());

    var dto = service.objectStructure(ConstructPathType.TRANSACTIONS, "TLogic").orElseThrow();

    assertThat(dto.logic())
            .extracting(LogicInfoDto::kind, LogicInfoDto::status, LogicInfoDto::required)
            .containsExactly(
                    tuple("execute", LogicStatus.CONFIGURED, true),
                    tuple("preview", LogicStatus.DEFAULT, false),
                    tuple("explain", LogicStatus.DEFAULT, false));
  }

  @Test
  void objectStructureReportsDefaultLogicForFunction() {
    GlobalManager.globalManager().registerFunction(
            Dsl.function("FLogic").execute(ctx -> Result.success("ok")).build());

    var dto = service.objectStructure(ConstructPathType.FUNCTIONS, "FLogic").orElseThrow();

    assertThat(dto.type()).isEqualTo("function");
    assertThat(dto.logic())
            .extracting(LogicInfoDto::kind, LogicInfoDto::status, LogicInfoDto::required)
            .containsExactly(
                    tuple("execute", LogicStatus.CONFIGURED, true),
                    tuple("preview", LogicStatus.DEFAULT, false),
                    tuple("explain", LogicStatus.DEFAULT, false));
  }

  @Test
  void objectStructureReportsHelperLogic() {
    GlobalManager.globalManager().registerHelper("HLogic", new Executable<String, Integer>() {
      @Override
      public Result<Integer> execute(Context<String> ctx) {
        return Result.success(1);
      }
    });

    var dto = service.objectStructure(ConstructPathType.HELPERS, "HLogic").orElseThrow();

    assertThat(dto.type()).isEqualTo("helper");
    assertThat(dto.logic())
            .extracting(LogicInfoDto::kind, LogicInfoDto::status, LogicInfoDto::required)
            .containsExactly(
                    tuple("execute", LogicStatus.CONFIGURED, true),
                    tuple("preview", LogicStatus.DEFAULT, false),
                    tuple("explain", LogicStatus.DEFAULT, false));
  }

  @Test
  void workingSetPropagateHelperFilenameFromHelperSource() {
    GlobalManager.globalManager().registerHelper("helperWithFile",
            new Executable<String, Integer>() {
              @Override
              public Result<Integer> execute(Context<String> ctx) {
                return Result.success(1);
              }

              @Override
              public ExecutableDescriptor describe() {
                return new ExecutableDescriptor(
                        "helperWithFile", "A helper", String.class, Integer.class,
                        List.of());
              }
            });
    GlobalManager.globalManager().registerFunction(
            Dsl.function("functionWithFile")
                    .parameters(p -> p.string("greeting"))
                    .execute(ctx -> Result.success("ok"))
                    .build());
    DslConfig.dslConfig().generatedClassRegistry().registerHelperSource(() -> List.of(
            new HelperSource.Entry("helperWithFile", "HelperWithFile.java"),
            new HelperSource.Entry("functionWithFile", "FunctionWithFile.java")));

    List<DefinitionMetaDto> definitions = service
            .workingSet(new ObjectSearchQuery(0, 500, null, null, null)).items();

    assertThat(definitions)
            .anySatisfy(d -> {
              assertThat(d.name()).isEqualTo("helperWithFile");
              assertThat(d.type()).isEqualTo("helper");
              assertThat(d.filePath()).isEqualTo("HelperWithFile.java");
            })
            .anySatisfy(d -> {
              assertThat(d.name()).isEqualTo("functionWithFile");
              assertThat(d.type()).isEqualTo("function");
              assertThat(d.filePath()).isEqualTo("FunctionWithFile.java");
            });
  }

  @Test
  void searchObjectsReturnsPagedResults() {
    registerSampleHelper("AHelper", "first helper", String.class, Integer.class);
    registerSampleHelper("BHelper", "second helper", String.class, String.class);
    registerSampleHelper("CHelper", "third helper", Long.class, Boolean.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(0, 2, null,
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.ALL, null);

    assertThat(page.items()).hasSize(2);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.offset()).isEqualTo(0);
    assertThat(page.limit()).isEqualTo(2);
  }

  @Test
  void searchObjectsRespectsPageAndSize() {
    registerSampleHelper("Alpha", "desc", String.class, Integer.class);
    registerSampleHelper("Beta", "desc", String.class, String.class);
    registerSampleHelper("Gamma", "desc", Long.class, Boolean.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(1, 1, null,
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.ALL, null);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).name()).isEqualTo("Beta");
    assertThat(page.total()).isEqualTo(3);
  }

  @Test
  void searchObjectsFiltersByQuery() {
    registerSampleHelper("FooHelper", "foo desc", String.class, Integer.class);
    registerSampleHelper("BarHelper", "bar desc", String.class, String.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(0, 10, "foo",
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.EXACT, null);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).name()).isEqualTo("FooHelper");
  }

  @Test
  void searchObjectsMatchesDescriptionAndInputType() {
    registerSampleHelper("FooHelper", "matches this", String.class, Integer.class);
    registerSampleHelper("BarHelper", "no match", String.class, String.class);

    PageResponse<ObjectSearchResult> descPage = service.searchObjects(0, 10, "matches",
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.EXACT, null);
    assertThat(descPage.total()).isEqualTo(1);
    assertThat(descPage.items().get(0).name()).isEqualTo("FooHelper");

    PageResponse<ObjectSearchResult> typePage = service.searchObjects(0, 10, "Integer",
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.EXACT, null);
    assertThat(typePage.total()).isEqualTo(1);
    assertThat(typePage.items().get(0).name()).isEqualTo("FooHelper");
  }

  @Test
  void searchObjectsAllModeIgnoresQuery() {
    registerSampleHelper("FooHelper", "bar description", String.class, Integer.class);
    registerSampleHelper("BarHelper", "other", String.class, String.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(0, 10, "bar",
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.ALL, null);

    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  void searchObjectsFiltersByDslTypeProcess() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PType").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("TType").execute(ctx -> Result.success("ok")).build());
    registerSampleHelper("HType", "helper desc", String.class, Integer.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(0, 50, null,
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.ALL, DslType.PROCESS);

    assertThat(page.items()).extracting("name").containsExactly("PType");
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  void searchObjectsFiltersByDslTypeOtherMatchesHelpers() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("POther").execute(ctx -> Result.success("ok")).build());
    registerSampleHelper("HOther", "helper desc", String.class, Integer.class);

    PageResponse<ObjectSearchResult> page = service.searchObjects(0, 50, null,
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.ALL, DslType.OTHER);

    assertThat(page.items()).extracting("name").containsExactly("HOther");
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  void workingSetFiltersByQueryTextAndDslType() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("AlphaProcess").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerProcess(
            Dsl.process("BetaProcess").execute(ctx -> Result.success("ok")).build());
    registerSampleHelper("AlphaHelper", "helper desc", String.class, Integer.class);

    var response = service.workingSet(new ObjectSearchQuery(0, 50, "alpha",
            cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode.EXACT, DslType.PROCESS));

    assertThat(response.items()).extracting("name").containsExactly("AlphaProcess");
    assertThat(response.total()).isEqualTo(1);
  }

  private void registerSampleHelper(String name, String description, Class<?> inputType,
          Class<?> outputType) {
    GlobalManager.globalManager().registerHelper(name, new Executable<String, Integer>() {
      @Override
      public Result<Integer> execute(Context<String> ctx) {
        return Result.success(1);
      }

      @Override
      public ExecutableDescriptor describe() {
        return new ExecutableDescriptor(name, description, inputType, outputType, List.of());
      }
    });
  }
}
