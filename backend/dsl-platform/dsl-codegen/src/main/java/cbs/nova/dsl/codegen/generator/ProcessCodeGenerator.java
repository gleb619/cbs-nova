package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.Util.importBlock;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.annotation.DslGenerated;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.process.DslTemporalProcess;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.SignalDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

import javax.annotation.processing.Generated;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public final class ProcessCodeGenerator {

  private final DslPackageNameResolver packageNameResolver;

  public @NonNull List<GeneratedSource> generate(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage,
          boolean useFileNameSubPackage) {
    String name = descriptor.name();
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String pkg = packageNameResolver.resolve(targetPackage, versionConstant, name,
            useFileNameSubPackage);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    var sources = List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, interfaceName, descriptor)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.inputType(), descriptor.signals())));
    log.atLevel(Level.DEBUG)
            .log(() -> "[ProcessCodeGenerator] Generated process %s v%s in package %s"
                    .formatted(name, versionConstant, pkg));
    return sources;
  }

  private @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String interfaceName, ProcessDescriptor descriptor) {
    String inputType = typeName(descriptor.inputType());
    List<SignalDescriptor> signals = descriptor.signals();
    String signalImports = signalImports(signals);
    String signalMethods = signalInterfaceMethods(signals);
    String importBlock = importBlock(DslTemporalProcess.class, DslTemporalProcessRequest.class,
            descriptor.inputType(), DslGenerated.class, Generated.class);
    String annotation = GeneratorMetadata.annotation(ProcessCodeGenerator.class);

    return Substitutor.format(// language=java
            """
                    package ${pkg};${importBlock}${signalImports}
                    import cbs.nova.dsl.process.DslTemporalProcessRequest;import io.temporal.workflow.QueryMethod;
                    import io.temporal.workflow.SignalMethod;
                    import io.temporal.workflow.WorkflowInterface;
                    import io.temporal.workflow.WorkflowMethod;

                    ${annotation}
                    @WorkflowInterface
                    public interface ${interfaceName} extends DslTemporalProcess<${inputType}> {

                      @QueryMethod
                      String getVersion();

                      @QueryMethod
                      java.util.Map<String, Object> dslSignalState();

                      ${signalMethods}

                      @Override
                      @WorkflowMethod
                      Object execute(DslTemporalProcessRequest<${inputType}> request);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "signalImports", signalImports,
                    "annotation", annotation,
                    "interfaceName", interfaceName,
                    "inputType", inputType,
                    "signalMethods", signalMethods));
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName,
          String versionConstant, Class<?> inputType, List<SignalDescriptor> signals) {
    String inputTypeName = typeName(inputType);
    String importBlock = importBlock(DslTemporalProcessRequest.class, inputType,
            GlobalManager.class, ProcessDslObject.class,
            DslGenerated.class, Generated.class,
            List.class);
    String signalImplImports = signalImports(signals);
    String annotation = GeneratorMetadata.annotation(ProcessCodeGenerator.class);

    String providerClass = processName + "GeneratedClassProvider";
    String signalMethods = signalImplMethods(signals);
    String signalBuffer = signalBufferClass();
    String metadataSetup = signals.isEmpty()
            ? ""
            : "\n    metadata.put(\"" + cbs.nova.dsl.config.Constants.SIGNAL_AWAITER_METADATA_KEY
                    + "\", signalBuffer);";

    String template = // language=java
            """
                    package ${pkg};${importBlock}${signalImplImports}
                    ${annotation}
                    public class ${implName} implements ${interfaceName} {

                      private static final String VERSION = "${version}";
                      private final SignalBuffer signalBuffer = new SignalBuffer();

                      @Override
                      public String getVersion() {
                        return VERSION;
                      }

                      @Override
                      public java.util.Map<String, Object> dslSignalState() {
                        return signalBuffer.snapshot();
                      }

                      ${signalMethods}

                      @Override
                      public Object execute(DslTemporalProcessRequest<${inputTypeName}> request) {
                        ${inputTypeName} input = request.payload();
                        var process = (ProcessDslObject) new ${providerClass}().dslObject();
                        java.util.Map<String, Object> metadata = new java.util.HashMap<>();${metadataSetup}
                        return GlobalManager.globalManager().runProcessWithCompensation(
                                request.runId(), input, process, metadata);
                      }

                      ${signalBuffer}
                    }
                    """;

    return Substitutor.format(
            template,
            Map.ofEntries(
                    Map.entry("pkg", pkg),
                    Map.entry("importBlock", importBlock),
                    Map.entry("signalImplImports", signalImplImports),
                    Map.entry("annotation", annotation),
                    Map.entry("processName", processName),
                    Map.entry("providerClass", providerClass),
                    Map.entry("interfaceName", interfaceName),
                    Map.entry("implName", implName),
                    Map.entry("version", versionConstant),
                    Map.entry("inputTypeName", inputTypeName),
                    Map.entry("signalMethods", signalMethods),
                    Map.entry("signalBuffer", signalBuffer),
                    Map.entry("metadataSetup", metadataSetup)));
  }

  private String signalImports(List<SignalDescriptor> signals) {
    if (signals.isEmpty()) {
      return "";
    }
    List<Class<?>> types = signals.stream().map(SignalDescriptor::payloadType)
            .filter(t -> t != null && !isInJavaLang(t)).distinct().toList();
    if (types.isEmpty()) {
      return "";
    }
    return "\n" + importBlock(types.toArray(new Class<?>[0]));
  }

  private boolean isInJavaLang(Class<?> type) {
    return type.getPackageName() != null && type.getPackageName().startsWith("java.lang");
  }

  private String signalInterfaceMethods(List<SignalDescriptor> signals) {
    if (signals.isEmpty()) {
      return "";
    }
    return signals.stream()
            .map(s -> "  @SignalMethod\n  void " + s.name() + "(" + typeName(s.payloadType())
                    + " payload);")
            .collect(Collectors.joining("\n\n"));
  }

  private String signalImplMethods(List<SignalDescriptor> signals) {
    if (signals.isEmpty()) {
      return "";
    }
    return signals.stream()
            .map(s -> "  @Override\n  public void " + s.name() + "(" + typeName(s.payloadType())
                    + " payload) {\n"
                    + "    signalBuffer.receive(\"" + s.name() + "\", payload);\n  }")
            .collect(Collectors.joining("\n\n"));
  }

  private String signalBufferClass() {
    return """
                      private static final class SignalBuffer implements cbs.nova.dsl.process.SignalAwaiter {
                        private final java.util.Map<String, Object> payloads = new java.util.concurrent.ConcurrentHashMap<>();
                        private final java.util.Set<String> received = java.util.concurrent.ConcurrentHashMap.newKeySet();

                        void receive(String name, Object payload) {
                          payloads.put(name, payload);
                          received.add(name);
                        }

                        java.util.Map<String, Object> snapshot() {
                          return java.util.Map.copyOf(payloads);
                        }

                        @Override
                        public <T> T awaitSignal(String name, Class<T> type) {
                          io.temporal.workflow.Workflow.await(() -> received.contains(name));
                          return type.cast(payloads.get(name));
                        }

                        @Override
                        public <T> T getSignalPayload(String name, Class<T> type) {
                          return type.cast(payloads.get(name));
                        }

                        @Override
                        public boolean signalReceived(String name) {
                          return received.contains(name);
                        }
                      }
            """;
  }

  private String typeName(Class<?> type) {
    return type == null ? "Object" : type.getSimpleName();
  }
}
