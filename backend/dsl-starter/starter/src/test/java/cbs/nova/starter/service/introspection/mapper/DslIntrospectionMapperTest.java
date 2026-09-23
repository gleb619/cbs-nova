package cbs.nova.starter.service.introspection.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.Descriptors;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

class DslIntrospectionMapperTest {

  private final DslIntrospectionMapper mapper = Mappers.getMapper(DslIntrospectionMapper.class);

  @Test
  void mapsDslDescriptorToHelperSearchResult() {
    DslDescriptor descriptor = Descriptors.from("fn",
            new ExecutableDescriptor(
                    "fn", "desc", String.class, Integer.class, List.of()));

    ObjectSearchResult result = mapper.toHelperSearchResult(descriptor);

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
            List.of());

    ObjectSearchResult result = mapper.toHelperSearchResult("helperName", descriptor);

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
            .compensation((ctx, history) -> ctx.log("ok"))
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
    DslDescriptor descriptor = Descriptors.from("fn",
            new ExecutableDescriptor(
                    "fn", "desc", String.class, Integer.class, List.of()));

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

  @Test
  void mapsVoidAndNullTypesToNullTypeNames() {
    // Quirk: Void.class and null both collapse to a null type name.
    ExecutableDescriptor voidDescriptor = new ExecutableDescriptor(
            "fn", "desc", Void.class, Void.class, List.of());
    ExecutableDescriptor nullDescriptor = new ExecutableDescriptor(
            "fn", "desc", null, null, List.of());

    ObjectSearchResult voidResult = mapper
            .toHelperSearchResult(Descriptors.from("fn", voidDescriptor));
    ObjectSearchResult nullResult = mapper
            .toHelperSearchResult(Descriptors.from("fn", nullDescriptor));

    assertThat(voidResult.inputType()).isNull();
    assertThat(voidResult.outputType()).isNull();
    assertThat(nullResult.inputType()).isNull();
    assertThat(nullResult.outputType()).isNull();
  }

  @Test
  void singleArgMetaOverloadsLeaveStatusAndFilePathNull() {
    ProcessDslObject process = Dsl.process("P")
            .execute(ctx -> Result.success("ok"))
            .build();
    TransactionDslObject tx = Dsl.transaction("T")
            .execute(ctx -> Result.success("ok"))
            .build();
    DslDescriptor descriptor = Descriptors.from("fn",
            new ExecutableDescriptor(
                    "fn", "desc", String.class, Integer.class, List.of()));
    ExecutableDescriptor helper = new ExecutableDescriptor(
            null, "desc", String.class, Integer.class, List.of());

    assertThat(mapper.toProcessDefinitionMeta(process).status()).isNull();
    assertThat(mapper.toProcessDefinitionMeta(process).filePath()).isNull();
    assertThat(mapper.toTransactionDefinitionMeta(tx).status()).isNull();
    assertThat(mapper.toTransactionDefinitionMeta(tx).filePath()).isNull();
    assertThat(mapper.toFunctionDefinitionMeta(descriptor).status()).isNull();
    assertThat(mapper.toFunctionDefinitionMeta(descriptor).filePath()).isNull();
    assertThat(mapper.toHelperDefinitionMeta("helperName", helper).status()).isNull();
    assertThat(mapper.toHelperDefinitionMeta("helperName", helper).filePath()).isNull();
  }

  @Test
  void mapsProcessDefinitionMetaWithSchemaStatusAndFilePath() {
    ProcessDslObject process = Dsl.process("P")
            .version("v1")
            .taskQueue("tq")
            .input(String.class)
            .output(Integer.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    Map<String, Object> inputSchema = Map.of("type", "string");

    DefinitionMetaDto dto = mapper.toProcessDefinitionMeta(
            process, inputSchema, DefinitionStatus.DRAFT, "flows/p.dsl");

    assertThat(dto.name()).isEqualTo("P");
    assertThat(dto.type()).isEqualTo("process");
    assertThat(dto.version()).isEqualTo("v1");
    assertThat(dto.taskQueue()).isEqualTo("tq");
    assertThat(dto.inputType()).isEqualTo("String");
    assertThat(dto.outputType()).isEqualTo("Integer");
    assertThat(dto.hasCompensation()).isFalse();
    assertThat(dto.inputSchema()).isEqualTo(inputSchema);
    assertThat(dto.status()).isEqualTo(DefinitionStatus.DRAFT);
    assertThat(dto.filePath()).isEqualTo("flows/p.dsl");
  }

  @Test
  void mapsTransactionDefinitionMetaWithSchemaStatusAndFilePath() {
    TransactionDslObject tx = Dsl.transaction("T")
            .version("v2")
            .taskQueue("tq2")
            .input(Long.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    Map<String, Object> inputSchema = Map.of("type", "integer");

    DefinitionMetaDto dto = mapper.toTransactionDefinitionMeta(
            tx, inputSchema, DefinitionStatus.MODIFIED, "flows/t.dsl");

    assertThat(dto.name()).isEqualTo("T");
    assertThat(dto.type()).isEqualTo("transaction");
    assertThat(dto.version()).isEqualTo("v2");
    assertThat(dto.taskQueue()).isEqualTo("tq2");
    assertThat(dto.inputType()).isEqualTo("Long");
    assertThat(dto.outputType()).isEqualTo("String");
    assertThat(dto.hasCompensation()).isFalse();
    assertThat(dto.inputSchema()).isEqualTo(inputSchema);
    assertThat(dto.status()).isEqualTo(DefinitionStatus.MODIFIED);
    assertThat(dto.filePath()).isEqualTo("flows/t.dsl");
  }

  @Test
  void mapsFunctionDefinitionMetaWithSchemaStatusAndFilePath() {
    DslDescriptor descriptor = Descriptors.from("fn",
            new ExecutableDescriptor(
                    "fn", "desc", String.class, Integer.class, List.of()));
    Map<String, Object> inputSchema = Map.of("type", "string");

    DefinitionMetaDto dto = mapper.toFunctionDefinitionMeta(
            descriptor, inputSchema, DefinitionStatus.PUBLISHED, "flows/f.dsl");

    assertThat(dto.name()).isEqualTo("fn");
    assertThat(dto.type()).isEqualTo("function");
    assertThat(dto.version()).isNull();
    assertThat(dto.taskQueue()).isNull();
    assertThat(dto.hasCompensation()).isNull();
    assertThat(dto.description()).isEqualTo("desc");
    assertThat(dto.inputSchema()).isEqualTo(inputSchema);
    assertThat(dto.status()).isEqualTo(DefinitionStatus.PUBLISHED);
    assertThat(dto.filePath()).isEqualTo("flows/f.dsl");
  }

  @Test
  void mapsHelperDefinitionMetaWithSchemaStatusAndFilePath() {
    ExecutableDescriptor helper = new ExecutableDescriptor(
            null, "desc", String.class, Integer.class, List.of());
    Map<String, Object> inputSchema = Map.of("type", "string");

    DefinitionMetaDto dto = mapper.toHelperDefinitionMeta(
            "helperName", helper, inputSchema, DefinitionStatus.DRAFT, "flows/h.dsl");

    assertThat(dto.name()).isEqualTo("helperName");
    assertThat(dto.type()).isEqualTo("helper");
    assertThat(dto.version()).isNull();
    assertThat(dto.taskQueue()).isNull();
    assertThat(dto.hasCompensation()).isNull();
    assertThat(dto.description()).isEqualTo("desc");
    assertThat(dto.inputSchema()).isEqualTo(inputSchema);
    assertThat(dto.status()).isEqualTo(DefinitionStatus.DRAFT);
    assertThat(dto.filePath()).isEqualTo("flows/h.dsl");
  }
}
