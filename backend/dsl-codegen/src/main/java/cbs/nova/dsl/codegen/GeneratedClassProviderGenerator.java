package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslObject;
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

  public @NonNull GeneratedSource forProcess(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String targetPackage) {
    return forProcess(descriptor, null, targetPackage);
  }

  public @NonNull GeneratedSource forProcess(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String version = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(name, version, targetPackage);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";
    String providerClass = name + "GeneratedClassProvider";

    return buildSource(pkg, providerClass, DslObject.DslType.PROCESS, descriptor.name(),
            version, descriptor.taskQueue(), interfaceName, implName,
            descriptor.inputType(), descriptor.outputType());
  }

  public @NonNull GeneratedSource forTransaction(
          @NonNull TransactionDescriptor descriptor,
          @Nullable String targetPackage) {
    return forTransaction(descriptor, null, targetPackage);
  }

  public @NonNull GeneratedSource forTransaction(
          @NonNull TransactionDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String version = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(name, version, targetPackage);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";
    String providerClass = name + "GeneratedClassProvider";

    return buildSource(pkg, providerClass, DslObject.DslType.TRANSACTION, descriptor.name(),
            version, descriptor.taskQueue(), interfaceName, implName,
            descriptor.inputType(), descriptor.outputType());
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private GeneratedSource buildSource(
          String pkg, String providerClass, DslObject.DslType type, String name,
          String version, String taskQueue, String interfaceName, String implName,
          Class<?> inputType, Class<?> outputType) {
    String inputLiteral = typeLiteral(inputType);
    String outputLiteral = typeLiteral(outputType);

    List<String> imports = new ArrayList<>();
    addImport(imports, inputType);
    addImport(imports, outputType);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";

    String source = Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.DslObject;
                    import cbs.nova.dsl.GeneratedClassDescriptor;
                    import cbs.nova.dsl.GeneratedClassProvider;

                    public final class ${providerClass} implements GeneratedClassProvider {
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
                                ${outputLiteral});
                      }
                    }
                    """,
            Map.ofEntries(
                    Map.entry("pkg", pkg),
                    Map.entry("importBlock", importBlock),
                    Map.entry("providerClass", providerClass),
                    Map.entry("type", type.name()),
                    Map.entry("name", name),
                    Map.entry("version", version),
                    Map.entry("taskQueue", taskQueue),
                    Map.entry("interfaceName", interfaceName),
                    Map.entry("implName", implName),
                    Map.entry("inputLiteral", inputLiteral),
                    Map.entry("outputLiteral", outputLiteral)));

    return new GeneratedSource(pkg, providerClass, source);
  }

  private String typeLiteral(Class<?> type) {
    return type == null ? "null" : type.getSimpleName() + ".class";
  }

  private void addImport(List<String> imports, Class<?> type) {
    if (type == null || type.getPackageName().startsWith("java.lang")) {
      return;
    }
    imports.add("import " + type.getCanonicalName() + ";");
  }
}
