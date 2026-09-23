package cbs.nova.starter.service.introspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.helper.HelperSource;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
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
import cbs.nova.starter.model.DslIntrospectionModels.LogicInfoDto;
import cbs.nova.starter.model.DslIntrospectionModels.LogicStatus;
import cbs.nova.starter.service.DslDefinitionStatusResolver;
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
                    new DslGitStatusResolver(DslProperties.builder().build(), null)));
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void processesReturnsRegisteredNames() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("P1").execute(ctx -> Result.success("ok")).build());

    assertThat(service.processes().names()).containsExactly("P1");
  }

  @Test
  void processDetailIncludesVersionAndTaskQueue() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("P1")
                    .version("v9")
                    .taskQueue("q1")
                    .input(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var detail = service.processDetail("P1").orElseThrow();

    assertThat(detail.version()).isEqualTo("v9");
    assertThat(detail.taskQueue()).isEqualTo("q1");
    assertThat(detail.inputType()).isEqualTo("String");
    assertThat(detail.inputSchema()).isNotNull();
  }

  @Test
  void transactionDetailIncludesTimeoutAndSchema() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("T1")
                    .input(Integer.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var detail = service.transactionDetail("T1").orElseThrow();

    assertThat(detail.startToCloseTimeoutMs()).isPositive();
    assertThat(detail.inputType()).isEqualTo("Integer");
    assertThat(detail.inputSchema()).isNotNull();
  }

  @Test
  void definitionsMapsProcessAndTransaction() {
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

    var definitions = service.definitions();

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
            .constructSchema("PSchema", ConstructSchemaMode.PREVIEW).orElseThrow();

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
            .constructSchema("TSchema", ConstructSchemaMode.PREVIEW).orElseThrow();

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
            .constructSchema("HSchema", ConstructSchemaMode.PREVIEW).orElseThrow();

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
            .constructSchema("FSchema", ConstructSchemaMode.PREVIEW).orElseThrow();

    assertThat(dto.type()).isEqualTo("function");
    assertThat(dto.inputSchema()).isNotNull();
    assertThat(dto.inputSchema()).containsKey("properties");
    assertThat((Map<String, Object>) dto.inputSchema().get("properties"))
            .containsKey("greeting");
  }

  @Test
  void constructSchemaReturnsEmptyForUnknown() {
    assertThat(service.constructSchema("NoSuchConstruct", ConstructSchemaMode.PREVIEW)).isEmpty();
    assertThat(service.constructSchema("NoSuchConstruct", ConstructSchemaMode.EXPLAIN)).isEmpty();
  }

  @Test
  void constructSchemaInExplainModeReturnsSchemaForProcess() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PExplain")
                    .input(String.class)
                    .output(Integer.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var dto = service.constructSchema("PExplain", ConstructSchemaMode.EXPLAIN).orElseThrow();

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

    var dto = service.constructSchema("TExplain", ConstructSchemaMode.EXPLAIN).orElseThrow();

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

    var dto = service.constructSchema("HExplain", ConstructSchemaMode.EXPLAIN).orElseThrow();

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

    var dto = service.constructSchema("FExplain", ConstructSchemaMode.EXPLAIN).orElseThrow();

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

    var dto = service.objectStructure("PLogic").orElseThrow();

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

    var dto = service.objectStructure("PLogicFull").orElseThrow();

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

    var dto = service.objectStructure("TLogic").orElseThrow();

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

    var dto = service.objectStructure("FLogic").orElseThrow();

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

    var dto = service.objectStructure("HLogic").orElseThrow();

    assertThat(dto.type()).isEqualTo("helper");
    assertThat(dto.logic())
            .extracting(LogicInfoDto::kind, LogicInfoDto::status, LogicInfoDto::required)
            .containsExactly(
                    tuple("execute", LogicStatus.CONFIGURED, true),
                    tuple("preview", LogicStatus.DEFAULT, false),
                    tuple("explain", LogicStatus.DEFAULT, false));
  }

  @Test
  void definitionsPropagateHelperFilenameFromHelperSource() {
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

    List<DefinitionMetaDto> definitions = service.definitions();

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
  void helpersReturnsPagedCatalog() {
    registerSampleHelper("AHelper", "first helper", String.class, Integer.class);
    registerSampleHelper("BHelper", "second helper", String.class, String.class);
    registerSampleHelper("CHelper", "third helper", Long.class, Boolean.class);

    PageResponse<HelperCatalogEntry> page = service.helpers(0, 2, null, null);

    assertThat(page.items()).hasSize(2);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.offset()).isEqualTo(0);
    assertThat(page.limit()).isEqualTo(2);
  }

  @Test
  void helpersRespectsOffsetAndLimit() {
    registerSampleHelper("Alpha", "desc", String.class, Integer.class);
    registerSampleHelper("Beta", "desc", String.class, String.class);
    registerSampleHelper("Gamma", "desc", Long.class, Boolean.class);

    PageResponse<HelperCatalogEntry> page = service.helpers(1, 1, null, null);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).name()).isEqualTo("Beta");
    assertThat(page.total()).isEqualTo(3);
  }

  @Test
  void helpersFiltersByName() {
    registerSampleHelper("FooHelper", "foo desc", String.class, Integer.class);
    registerSampleHelper("BarHelper", "bar desc", String.class, String.class);

    PageResponse<HelperCatalogEntry> page = service.helpers(0, 10, "foo", "exact");

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).name()).isEqualTo("FooHelper");
  }

  @Test
  void helpersFiltersByDescription() {
    registerSampleHelper("FooHelper", "matches this", String.class, Integer.class);
    registerSampleHelper("BarHelper", "no match", String.class, String.class);

    PageResponse<HelperCatalogEntry> page = service.helpers(0, 10, "matches", "exact");

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).name()).isEqualTo("FooHelper");
  }

  @Test
  void helpersFiltersByAllMode() {
    registerSampleHelper("FooHelper", "bar description", String.class, Integer.class);
    registerSampleHelper("BarHelper", "other", String.class, String.class);

    PageResponse<HelperCatalogEntry> page = service.helpers(0, 10, "bar", "exact");

    assertThat(page.total()).isEqualTo(2);
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
