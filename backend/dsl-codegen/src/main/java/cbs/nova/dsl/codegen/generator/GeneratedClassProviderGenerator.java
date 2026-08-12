package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.EscapeUtil.escapeJavaString;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.AstExtractor;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class GeneratedClassProviderGenerator {

  private final CodegenNaming codegenNaming;
  private final AstExtractor executeAstJsonExtractor;

  public @NonNull GeneratedSource forProcess(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    return forProcess(descriptor, List.of(), buildVersion, targetPackage);
  }

  public @NonNull GeneratedSource forProcess(
          @NonNull ProcessDescriptor descriptor,
          @NonNull List<String> preprocessedSources,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String version = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(name, version, targetPackage);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";
    String providerClass = name + "GeneratedClassProvider";
    String executeJson = executeAstJsonExtractor.extract(
            preprocessedSources, name, DslType.PROCESS);

    return buildSource(pkg, providerClass, DslObject.DslType.PROCESS, descriptor.name(),
            version, descriptor.taskQueue(), interfaceName, implName,
            descriptor.inputType(), descriptor.outputType(), executeJson);
  }

  public @NonNull GeneratedSource forTransaction(
          @NonNull TransactionDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    return forTransaction(descriptor, List.of(), buildVersion, targetPackage);
  }

  public @NonNull GeneratedSource forTransaction(
          @NonNull TransactionDescriptor descriptor,
          @NonNull List<String> preprocessedSources,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String version = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(name, version, targetPackage);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";
    String providerClass = name + "GeneratedClassProvider";
    String executeJson = executeAstJsonExtractor.extract(
            preprocessedSources, name, DslType.TRANSACTION);

    return buildSource(pkg, providerClass, DslObject.DslType.TRANSACTION, descriptor.name(),
            version, descriptor.taskQueue(), interfaceName, implName,
            descriptor.inputType(), descriptor.outputType(), executeJson);
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private GeneratedSource buildSource(
          String pkg, String providerClass, DslObject.DslType type, String name,
          String version, String taskQueue, String interfaceName, String implName,
          Class<?> inputType, Class<?> outputType, String executeJson) {
    String inputLiteral = typeLiteral(inputType);
    String outputLiteral = typeLiteral(outputType);
    String executeJsonLiteral = escapeJavaString(executeJson);

    List<String> imports = new ArrayList<>();
    addImport(imports, inputType);
    addImport(imports, outputType);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";
    String annotation = GeneratorMetadata.annotation(GeneratedClassProviderGenerator.class);

    String source = Substitutor.format(//language=java
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.DslGenerated;
                    import cbs.nova.dsl.DslObject;
                    import cbs.nova.dsl.GeneratedClassDescriptor;
                    import cbs.nova.dsl.GeneratedClassProvider;
                    import javax.annotation.processing.Generated;
                    
                    ${annotation}
                    public final class ${providerClass} implements GeneratedClassProvider {
                    
                      private static final String JSON_SPEC = "${executeJsonLiteral}";
                    
                      @Override
                      public GeneratedClassDescriptor descriptor() {
                        return new GeneratedClassDescriptor(
                                "${name}",
                                DslObject.DslType.${type},
                                "${version}",
                                "${taskQueue}",
                                ${interfaceName}.class,
                                ${implName}.class,
                                ${inputLiteral},
                                ${outputLiteral},
                                JSON_SPEC
                                );
                      }
                    
                      @Override
                      public String executeJson() {
                        return descriptor().executeJson();
                      }
                    
                      @Override
                      public Object implementationInstance() {
                        return new ${implName}();
                      }
                    
                    }
                    """,
            Map.ofEntries(
                    Map.entry("pkg", pkg),
                    Map.entry("importBlock", importBlock),
                    Map.entry("annotation", annotation),
                    Map.entry("providerClass", providerClass),
                    Map.entry("type", type.name()),
                    Map.entry("name", name),
                    Map.entry("version", version),
                    Map.entry("taskQueue", taskQueue),
                    Map.entry("interfaceName", interfaceName),
                    Map.entry("implName", implName),
                    Map.entry("inputLiteral", inputLiteral),
                    Map.entry("outputLiteral", outputLiteral),
                    Map.entry("executeJsonLiteral", executeJsonLiteral)));

    return new GeneratedSource(pkg, providerClass, source);
  }

  private String typeLiteral(Class<?> type) {
    return type == null ? "null" : type.getSimpleName() + ".class";
  }

  private void addImport(List<String> imports, Class<?> type) {
    if (type == null || type.getPackageName().startsWith("java.lang")) {
      return;
    }
    imports.add("import %s;".formatted(type.getCanonicalName()));
  }
}
