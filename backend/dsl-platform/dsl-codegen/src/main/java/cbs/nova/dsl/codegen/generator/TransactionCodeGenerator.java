package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.Util.importBlock;

import cbs.nova.dsl.annotation.DslGenerated;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.transaction.DslTemporalTransactionRequest;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

import javax.annotation.processing.Generated;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class TransactionCodeGenerator {

  private final DslPackageNameResolver packageNameResolver;

  public @NonNull List<GeneratedSource> generate(
          @NonNull TransactionDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage,
          boolean useFileNameSubPackage) {
    String name = descriptor.name();
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String pkg = packageNameResolver.resolve(targetPackage, versionConstant, name,
            useFileNameSubPackage);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";
    String inputTypeName = typeName(descriptor.inputType());

    var sources = List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, name, interfaceName, inputTypeName,
                            descriptor.inputType())),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue(), inputTypeName,
                            descriptor.inputType())));
    log.atLevel(Level.DEBUG)
            .log(() -> "[TransactionCodeGenerator] Generated transaction %s v%s in package %s"
                    .formatted(name, versionConstant, pkg));
    return sources;
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String transactionName, String interfaceName,
          String inputTypeName, Class<?> inputType) {
    String importBlock = importBlock(inputType, DslTemporalTransactionRequest.class,
            DslGenerated.class, Generated.class);
    String annotation = GeneratorMetadata.annotation(TransactionCodeGenerator.class);
    return Substitutor.format(// language=java
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.transaction.GeneratedTransactionActivity;
                    import io.temporal.activity.ActivityInterface;
                    import io.temporal.activity.ActivityMethod;

                    ${annotation}
                    @ActivityInterface(namePrefix = "${transactionName}_")
                    public interface ${interfaceName} extends GeneratedTransactionActivity<${inputTypeName}> {

                      @ActivityMethod
                      String getVersion();

                      @Override
                      @ActivityMethod
                      Object execute(DslTemporalTransactionRequest<${inputTypeName}> request);

                      @ActivityMethod
                      void compensate(DslTemporalTransactionRequest<${inputTypeName}> request, Throwable error);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "annotation", annotation,
                    "interfaceName", interfaceName,
                    "transactionName", transactionName,
                    "inputTypeName", inputTypeName));
  }

  private String generateImpl(String pkg, String transactionName, String interfaceName,
          String implName, String versionConstant, String taskQueue, String inputTypeName,
          Class<?> inputType) {
    String importBlock = importBlock(inputType, DslTemporalTransactionRequest.class,
            DslGenerated.class, Generated.class);
    String annotation = GeneratorMetadata.annotation(TransactionCodeGenerator.class);

    // TODO: add new method that allow to transfer whole object, like in `ProcessCodeGenerator`
    return Substitutor.format(// language=java
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.GlobalManager;

                    ${annotation}
                    public class ${implName} implements ${interfaceName} {

                      private static final String VERSION = "${version}";

                      @Override
                      public String getVersion() {
                        return VERSION;
                      }

                      @Override
                      public Object execute(DslTemporalTransactionRequest<${inputTypeName}> request) {
                        return GlobalManager.globalManager().runTransactionWithCompensation(
                                "${transactionName}", request.runId(), request.payload());
                      }

                      @Override
                      public void compensate(DslTemporalTransactionRequest<${inputTypeName}> request, Throwable error) {
                        GlobalManager.globalManager().compensateTransaction(
                                "${transactionName}", request.runId(), request.payload(), error);
                      }
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "annotation", annotation,
                    "transactionName", transactionName,
                    "interfaceName", interfaceName,
                    "implName", implName,
                    "version", versionConstant,
                    "inputTypeName", inputTypeName));
  }

  private String typeName(Class<?> type) {
    return type == null ? "Object" : type.getSimpleName();
  }
}
