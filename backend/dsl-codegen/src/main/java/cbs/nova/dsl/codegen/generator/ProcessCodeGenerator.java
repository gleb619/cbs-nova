package cbs.nova.dsl.codegen.generator;

import cbs.nova.dsl.DslGenerated;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ProcessCompensation;
import cbs.nova.dsl.ProcessMain;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Generated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class ProcessCodeGenerator {

  private final CodegenNaming codegenNaming;

  public @NonNull List<GeneratedSource> generate(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(descriptor.name(), versionConstant,
            targetPackage);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, interfaceName, descriptor)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.inputType(),
                            descriptor.transactionRefs())));
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String interfaceName, ProcessDescriptor descriptor) {
    String inputType = typeName(descriptor.inputType());
    List<String> imports = new ArrayList<>();
    addImport(imports, DslTemporalProcess.class);
    addImport(imports, DslTemporalProcessRequest.class);
    addImport(imports, descriptor.inputType());
    addImport(imports, DslGenerated.class);
    addImport(imports, Generated.class);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";
    String annotation = GeneratorMetadata.annotation(ProcessCodeGenerator.class);

    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import io.temporal.workflow.QueryMethod;
                    import io.temporal.workflow.WorkflowInterface;
                    import io.temporal.workflow.WorkflowMethod;

                    ${annotation}
                    @WorkflowInterface
                    public interface ${interfaceName} extends DslTemporalProcess<${inputType}> {

                      @QueryMethod
                      String getVersion();

                      @Override
                      @WorkflowMethod
                      Object execute(DslTemporalProcessRequest<${inputType}> request);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "annotation", annotation,
                    "interfaceName", interfaceName,
                    "inputType", inputType));
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName,
          String versionConstant, Class<?> inputType, List<String> transactionRefs) {
    String inputTypeName = typeName(inputType);
    List<String> imports = new ArrayList<>();
    addImport(imports, DslTemporalProcessRequest.class);
    addImport(imports, inputType);
    addImport(imports, GlobalManager.class);
    addImport(imports, ProcessMain.class);
    addImport(imports, ProcessCompensation.class);
    addImport(imports, DslGenerated.class);
    addImport(imports, Generated.class);
    imports.add("import java.util.List;");

    String importBlock = "\n" + String.join("\n", imports) + "\n";
    String annotation = GeneratorMetadata.annotation(ProcessCodeGenerator.class);

    String template = """
            package ${pkg};${importBlock}
            ${annotation}
            public class ${implName} implements ${interfaceName} {

              private static final String VERSION = "${version}";
              private static final List<String> TRANSACTION_REFS = ${transactionRefs};

              @Override
              public String getVersion() {
                return VERSION;
              }

              @Override
              public Object execute(DslTemporalProcessRequest<${inputTypeName}> request) {
                ${inputTypeName} input = request.payload();
                return GlobalManager.globalManager().runProcessWithCompensation(
                        request.runId(),
                        input,
                        ctx -> GlobalManager.globalManager().runProcess("${processName}", VERSION, ctx),
                        (compCtx, error) -> GlobalManager.globalManager()
                                .compensateProcess("${processName}", compCtx, error));
              }
            }
            """;

    String transactionRefsValue = transactionRefsLiteral(transactionRefs);
    return Substitutor.format(
            template,
            Map.ofEntries(
                    Map.entry("pkg", pkg),
                    Map.entry("importBlock", importBlock),
                    Map.entry("annotation", annotation),
                    Map.entry("processName", processName),
                    Map.entry("interfaceName", interfaceName),
                    Map.entry("implName", implName),
                    Map.entry("version", versionConstant),
                    Map.entry("inputTypeName", inputTypeName),
                    Map.entry("transactionRefs", transactionRefsValue)));
  }

  private String transactionRefsLiteral(List<String> refs) {
    if (refs.isEmpty()) {
      return "List.of()";
    }
    return "List.of(" + refs.stream()
            .map(s -> String.format("\"%s\"", s))
            .collect(Collectors.joining(", "))
            + ")";
  }

  private String typeName(Class<?> type) {
    return type == null ? "Object" : type.getSimpleName();
  }

  private void addImport(List<String> imports, Class<?> type) {
    if (type == null || type.getPackageName().startsWith("java.lang")) {
      return;
    }
    imports.add("import " + type.getCanonicalName() + ";");
  }
}
