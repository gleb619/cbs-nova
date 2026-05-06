package cbs.dsl.codegen;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Domain-oriented generator for {@code @DslComponent(type = MASS_OPERATION)} components.
 *
 * <p>Produces a single artifact per component: {@code {Code}MassOperationDefinition} — implements
 * {@code MassOperationDefinition} and hosts {@code dsl()} returning a {@code DslObject}.
 */
@RequiredArgsConstructor
public class MassOperationCodeGenerator {

  private static final String MO_INPUT = "cbs.dsl.api.MassOperationTypes.MassOperationInput";
  private static final String MO_OUTPUT = "cbs.dsl.api.MassOperationTypes.MassOperationOutput";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      generateDefinition(spec);
    }
  }

  private void generateDefinition(RegistrationSpec spec) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(MO_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(MO_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)", Map.of("inputType", simpleName(spec.inputType())));

    String outputConversion = outputIsRuntime
        ? "out"
        : "new MassOperationOutput(out.processedCount(), out.failedCount(), out.status())";

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : Substitutor.format("import {{type}};\n", Map.of("type", spec.inputType()));
    String outputTypeImport =
        outputIsRuntime ? "" : Substitutor.format("import {{type}};\n", Map.of("type", spec.outputType()));

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return MassOperationDsl.massOperation(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.MassOperationDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {{package}};
        
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.LockDefinition;
        import cbs.dsl.api.MassOperationDefinition;
        import cbs.dsl.api.MassOperationTypes.MassOperationInput;
        import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
        import cbs.dsl.api.SignalTypes;
        import cbs.dsl.api.SourceDefinition;
        import cbs.dsl.api.TriggerDefinition;
        import cbs.dsl.api.context.MassOperationContext;
        {{jsonPayloadImport}}        import {{packageName}}.{{className}};
        {{inputTypeImport}}{{outputTypeImport}}        {{dslImports}}        import java.util.Collections;
        import java.util.List;
        import java.util.function.Consumer;
        import javax.annotation.processing.Generated;
        
        /**
         * Generated MassOperationDefinition wrapper for {{className}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.MassOperationCodeGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements MassOperationDefinition {
        
            private final {{className}} function;
        
            public {{wrapperClassName}}() {
                this(null);
            }
        
            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({{className}}.class) : new {{className}}();
            }
        
            @Override
            public String getCode() {
                return "{{code}}";
            }
        
            @Override
            public String getCategory() {
                return "DEFAULT";
            }
        
            @Override
            public List<TriggerDefinition> getTriggers() {
                return Collections.emptyList();
            }
        
            @Override
            public SourceDefinition getSource() {
                return null;
            }
        
            @Override
            public Consumer<MassOperationContext> getItemBlock() {
                return ctx -> {};
            }
        
            @Override
            public MassOperationOutput execute(MassOperationInput input) {
                {{inputType}} typed = {{inputConversion}};
                {{outputType}} out = function.execute(typed);
                return {{outputConversion}};
            }
        
            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """;

    String source = Substitutor.format(
        sourceTemplate,
        Map.ofEntries(
            Map.entry("package", DEFINITIONS_PACKAGE),
            Map.entry("jsonPayloadImport", jsonPayloadImport),
            Map.entry("packageName", spec.packageName()),
            Map.entry("className", spec.className()),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputTypeImport", outputTypeImport),
            Map.entry("timestamp", timestamp),
            Map.entry("wrapperClassName", wrapperClassName),
            Map.entry("code", spec.code()),
            Map.entry("inputType", simpleName(spec.inputType())),
            Map.entry("inputConversion", inputConversion),
            Map.entry("outputType", simpleName(spec.outputType())),
            Map.entry("outputConversion", outputConversion),
            Map.entry("dslBody", dslBodyOrFallback),
            Map.entry("dslImports", dslImportsBlock)));

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
