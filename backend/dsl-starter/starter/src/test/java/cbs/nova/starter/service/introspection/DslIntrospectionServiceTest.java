package cbs.nova.starter.service.introspection;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.starter.service.introspection.mapper.DslIntrospectionMapper;
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
            DslConfig.dslConfig().jsonSchemaGenerator().get(), mapper);
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
            });
  }
}
