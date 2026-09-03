package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.EscapeUtil.escapeJavaString;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.AstExtractor;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public final class GeneratedClassProviderGenerator {

  private final AstExtractor executeAstJsonExtractor;
  private final DslPackageNameResolver packageNameResolver;


  public @NonNull GeneratedSource forProcess(
          @NonNull ProcessDescriptor descriptor,
          @NonNull List<String> preprocessedSources,
          @Nullable String buildVersion,
          @Nullable String targetPackage,
          boolean useFileNameSubPackage) {
    String name = descriptor.name();
    String version = resolveVersion(descriptor.version(), buildVersion);
    String pkg = packageNameResolver.resolve(targetPackage, version, name, useFileNameSubPackage);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";
    String providerClass = name + "GeneratedClassProvider";
    String executeJson = executeAstJsonExtractor.extract(
            preprocessedSources, name, DslType.PROCESS);
    String dslSourceClass = findDslSourceClassName(name, preprocessedSources);

    var source = buildSource(pkg, providerClass, DslObject.DslType.PROCESS, descriptor.name(),
            version, descriptor.taskQueue(), interfaceName, implName,
            descriptor.inputType(), descriptor.outputType(), executeJson, dslSourceClass);
    log.atLevel(Level.DEBUG)
            .log(() -> "[GeneratedClassProviderGenerator] Generated process provider class %s"
                    .formatted(providerClass));
    return source;
  }

  public @NonNull GeneratedSource forTransaction(
          @NonNull TransactionDescriptor descriptor,
          @NonNull List<String> preprocessedSources,
          @Nullable String buildVersion,
          @Nullable String targetPackage,
          boolean useFileNameSubPackage) {
    String name = descriptor.name();
    String version = resolveVersion(descriptor.version(), buildVersion);
    String pkg = packageNameResolver.resolve(targetPackage, version, name, useFileNameSubPackage);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";
    String providerClass = name + "GeneratedClassProvider";
    String executeJson = executeAstJsonExtractor.extract(
            preprocessedSources, name, DslType.TRANSACTION);
    String dslSourceClass = findDslSourceClassName(name, preprocessedSources);

    var source = buildSource(pkg, providerClass, DslObject.DslType.TRANSACTION, descriptor.name(),
            version, descriptor.taskQueue(), interfaceName, implName,
            descriptor.inputType(), descriptor.outputType(), executeJson, dslSourceClass);
    log.atLevel(Level.DEBUG)
            .log(() -> "[GeneratedClassProviderGenerator] Generated transaction provider class %s"
                    .formatted(providerClass));
    return source;
  }

  private @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private GeneratedSource buildSource(
          String pkg, String providerClass, DslObject.DslType type, String name,
          String version, String taskQueue, String interfaceName, String implName,
          Class<?> inputType, Class<?> outputType, String executeJson,
          @Nullable String dslSourceClass) {
    String inputLiteral = typeLiteral(inputType);
    String outputLiteral = typeLiteral(outputType);
    String executeJsonLiteral = escapeJavaString(executeJson);
    String dslObjectMethod = buildDslObjectMethod(dslSourceClass, name);

    List<String> imports = new ArrayList<>();
    addImport(imports, inputType);
    addImport(imports, outputType);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";
    String annotation = GeneratorMetadata.annotation(GeneratedClassProviderGenerator.class);

    String source = Substitutor.format(// language=java
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.annotation.DslGenerated;
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

                      ${dslObjectMethod}
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
                    Map.entry("executeJsonLiteral", executeJsonLiteral),
                    Map.entry("dslObjectMethod", dslObjectMethod)));

    log.atLevel(Level.DEBUG).log(() -> "[GeneratedClassProviderGenerator] Built source for %s"
            .formatted(providerClass));
    return new GeneratedSource(pkg, providerClass, source);
  }

  private String buildDslObjectMethod(String dslSourceClass, String name) {
    if (dslSourceClass == null) {
      return "";
    }
    return """
@Override
  public DslObject dslObject() {
    return new %s().byName("%s").orElseThrow();
  }
""".formatted(dslSourceClass, name).stripTrailing();
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

  private @Nullable String findDslSourceClassName(
          @NonNull String dslObjectName,
          @NonNull List<String> preprocessedSources) {
    var dslReference = Pattern.compile(
            "Dsl\\.(process|transaction|function)\\s*\\(\\s*\""
                    + Pattern.quote(dslObjectName) + "\"\\s*\\)");
    for (String preprocessedSource : preprocessedSources) {
      if (dslReference.matcher(preprocessedSource).find()) {
        return extractClassName(preprocessedSource);
      }
    }
    return null;
  }

  private @Nullable String extractClassName(@NonNull String preprocessedSource) {
    var classPattern = Pattern.compile(
            "public\\s+class\\s+(\\w+)\\s+implements\\s+DslCompactSource");
    var matcher = classPattern.matcher(preprocessedSource);
    return matcher.find() ? matcher.group(1) : null;
  }
}
