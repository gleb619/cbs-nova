package cbs.nova.starter.service.introspection.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.TransactionDetail;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.util.List;

class DslIntrospectionMapperTest {

  private final DslIntrospectionMapper mapper = Mappers.getMapper(DslIntrospectionMapper.class);

  @Test
  void mapsProcessDslObjectToDetail() {
    ProcessDslObject process = Dsl.process("P")
            .version("v2")
            .taskQueue("tq")
            .input(String.class)
            .output(Integer.class)
            .execute(ctx -> Result.success("ok"))
            .build();

    ProcessDetail detail = mapper.toProcessDetail(process);

    assertThat(detail.name()).isEqualTo("P");
    assertThat(detail.version()).isEqualTo("v2");
    assertThat(detail.taskQueue()).isEqualTo("tq");
    assertThat(detail.inputType()).isEqualTo("String");
    assertThat(detail.outputType()).isEqualTo("Integer");
    assertThat(detail.hasCompensation()).isFalse();
    assertThat(detail.inputSchema()).isNull();
  }

  @Test
  void mapsProcessDslObjectWithCompensation() {
    ProcessDslObject process = Dsl.process("P")
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("ok"))
            .build();

    ProcessDetail detail = mapper.toProcessDetail(process);

    assertThat(detail.hasCompensation()).isTrue();
  }

  @Test
  void mapsTransactionDslObjectToDetail() {
    TransactionDslObject tx = Dsl.transaction("T")
            .version("v3")
            .taskQueue("tq2")
            .input(Long.class)
            .output(String.class)
            .startToCloseTimeout(Duration.ofMinutes(2))
            .execute(ctx -> Result.success("ok"))
            .build();

    TransactionDetail detail = mapper.toTransactionDetail(tx);

    assertThat(detail.name()).isEqualTo("T");
    assertThat(detail.version()).isEqualTo("v3");
    assertThat(detail.taskQueue()).isEqualTo("tq2");
    assertThat(detail.inputType()).isEqualTo("Long");
    assertThat(detail.outputType()).isEqualTo("String");
    assertThat(detail.hasCompensation()).isFalse();
    assertThat(detail.startToCloseTimeoutMs()).isEqualTo(120_000L);
    assertThat(detail.inputSchema()).isNull();
  }

  @Test
  void mapsDslDescriptorToHelperSearchResult() {
    DslDescriptor descriptor = new DslDescriptor(
            "fn",
            DslType.FUNCTION,
            "desc",
            String.class,
            Integer.class,
            false,
            false,
            null,
            List.of(),
            null,
            null,
            null,
            null);

    HelperSearchResult result = mapper.toHelperSearchResult(descriptor);

    assertThat(result.name()).isEqualTo("fn");
    assertThat(result.type()).isEqualTo("function");
    assertThat(result.description()).isEqualTo("desc");
    assertThat(result.inputType()).isEqualTo("String");
    assertThat(result.outputType()).isEqualTo("Integer");
  }

  @Test
  void mapsExecutableDescriptorToHelperSearchResultWithHelperTypeAndGivenName() {
    ExecutableDescriptor descriptor = new ExecutableDescriptor(
            null,
            "desc",
            String.class,
            Integer.class,
            false,
            null,
            List.of());

    HelperSearchResult result = mapper.toHelperSearchResult("helperName", descriptor);

    assertThat(result.name()).isEqualTo("helperName");
    assertThat(result.type()).isEqualTo("helper");
    assertThat(result.description()).isEqualTo("desc");
    assertThat(result.inputType()).isEqualTo("String");
    assertThat(result.outputType()).isEqualTo("Integer");
  }

  @Test
  void mapsProcessDslObjectToDefinitionMetaDto() {
    ProcessDslObject process = Dsl.process("P")
            .version("v1")
            .taskQueue("tq")
            .input(String.class)
            .output(Integer.class)
            .execute(ctx -> Result.success("ok"))
            .build();

    DefinitionMetaDto dto = mapper.toProcessDefinitionMeta(process);

    assertThat(dto.name()).isEqualTo("P");
    assertThat(dto.type()).isEqualTo("process");
    assertThat(dto.version()).isEqualTo("v1");
    assertThat(dto.taskQueue()).isEqualTo("tq");
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.outputType()).isEqualTo("Integer");
    assertThat(dto.hasCompensation()).isFalse();
    assertThat(dto.inputSchema()).isNull();
  }

  @Test
  void mapsProcessDslObjectWithCompensationToDefinitionMetaDto() {
    ProcessDslObject process = Dsl.process("P")
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("ok"))
            .build();

    DefinitionMetaDto dto = mapper.toProcessDefinitionMeta(process);

    assertThat(dto.hasCompensation()).isTrue();
  }

  @Test
  void mapsTransactionDslObjectToDefinitionMetaDto() {
    TransactionDslObject tx = Dsl.transaction("T")
            .version("v2")
            .taskQueue("tq2")
            .input(Long.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();

    DefinitionMetaDto dto = mapper.toTransactionDefinitionMeta(tx);

    assertThat(dto.name()).isEqualTo("T");
    assertThat(dto.type()).isEqualTo("transaction");
    assertThat(dto.version()).isEqualTo("v2");
    assertThat(dto.taskQueue()).isEqualTo("tq2");
    assertThat(dto.inputType()).isEqualTo("Long");
    assertThat(dto.outputType()).isEqualTo("String");
    assertThat(dto.hasCompensation()).isFalse();
    assertThat(dto.inputSchema()).isNull();
  }

  @Test
  void mapsDslDescriptorToDefinitionMetaDto() {
    DslDescriptor descriptor = new DslDescriptor(
            "fn",
            DslType.FUNCTION,
            "desc",
            String.class,
            Integer.class,
            false,
            false,
            null,
            List.of(),
            null,
            null,
            null,
            null);

    DefinitionMetaDto dto = mapper.toFunctionDefinitionMeta(descriptor);

    assertThat(dto.name()).isEqualTo("fn");
    assertThat(dto.type()).isEqualTo("function");
    assertThat(dto.version()).isNull();
    assertThat(dto.taskQueue()).isNull();
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.outputType()).isEqualTo("Integer");
    assertThat(dto.hasCompensation()).isNull();
    assertThat(dto.description()).isEqualTo("desc");
    assertThat(dto.inputSchema()).isNull();
  }

  @Test
  void mapsExecutableDescriptorToDefinitionMetaDto() {
    ExecutableDescriptor descriptor = new ExecutableDescriptor(
            null,
            "desc",
            String.class,
            Integer.class,
            false,
            null,
            List.of());

    DefinitionMetaDto dto = mapper.toHelperDefinitionMeta("helperName", descriptor);

    assertThat(dto.name()).isEqualTo("helperName");
    assertThat(dto.type()).isEqualTo("helper");
    assertThat(dto.version()).isNull();
    assertThat(dto.taskQueue()).isNull();
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.outputType()).isEqualTo("Integer");
    assertThat(dto.hasCompensation()).isNull();
    assertThat(dto.description()).isEqualTo("desc");
    assertThat(dto.inputSchema()).isNull();
  }
}
