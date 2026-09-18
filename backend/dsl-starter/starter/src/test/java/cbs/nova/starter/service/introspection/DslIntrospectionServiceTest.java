package cbs.nova.starter.service.introspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.jsonschema.JacksonJsonSchemaGenerator;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
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
  private DslRuntime dslRuntime;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    DslIntrospectionMapper mapper = Mappers.getMapper(DslIntrospectionMapper.class);
    dslRuntime = mock(DslRuntime.class);
    service = new DslIntrospectionService(
            new JacksonJsonSchemaGenerator(),
            mapper,
            new DslDefinitionStatusResolver(DslProperties.builder().build(),
                    new DslGitStatusResolver(DslProperties.builder().build(), null)),
            dslRuntime);
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
                false,
                null,
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
  void constructSchemaInExplainModeReturnsExplainReport() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PExplain")
                    .input(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    ExplainReport report = ExplainReport.builder()
            .name("PExplain")
            .description("does things")
            .mermaid("graph TD; A-->B;")
            .build();
    when(dslRuntime.explain(any(), any())).thenReturn(report);

    Object value = service.constructSchema("PExplain", ConstructSchemaMode.EXPLAIN).orElseThrow();

    assertThat(value).isSameAs(report);
    verify(dslRuntime).explain(any(), any());
  }
}
