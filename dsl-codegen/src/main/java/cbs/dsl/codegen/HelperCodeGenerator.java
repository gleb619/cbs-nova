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
 * Domain-oriented generator for {@code @DslComponent(type = HELPER)} components.
 *
 * <p>Produces two artifacts per component:
 *
 * <ol>
 *   <li>{@code {Code}Activity} — Temporal {@code @ActivityInterface}.
 *   <li>{@code {Code}HelperDefinition} — implements both {@code HelperDefinition} and
 *       {@code {Code}Activity}. This is the single file that hosts the DSL metadata, the business
 *       delegation, and the Temporal activity contract.
 * </ol>
 */
@RequiredArgsConstructor
public class HelperCodeGenerator {

  private static final String HL_INPUT = "cbs.dsl.api.HelperTypes.HelperInput";
  private static final String HL_OUTPUT = "cbs.dsl.api.HelperTypes.HelperOutput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      generateActivityInterface(spec);
      generateDefinition(spec);
    }
  }

  private void generateActivityInterface(RegistrationSpec spec) throws IOException {
    String className = spec.className() + "Activity";
    String fqcn = GENERATED_PACKAGE + "." + className;

    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String sourceTemplate = //language=java
        """
        package {{GENERATED_PACKAGE}};
        
        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        import io.temporal.activity.ActivityInterface;
        import io.temporal.activity.ActivityMethod;
        import javax.annotation.processing.Generated;
        
        @Generated(
            value = "cbs.dsl.codegen.HelperCodeGenerator",
            date = "{{timestamp}}"
        )
        @ActivityInterface
        public interface {{className}} {
        
            @ActivityMethod
            HelperOutput execute(HelperInput input);
        }
        """;

    String source = Substitutor.format(sourceTemplate, Map.ofEntries(
        Map.entry("GENERATED_PACKAGE", GENERATED_PACKAGE),
        Map.entry("timestamp", timestamp),
        Map.entry("className", className)));

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private void generateDefinition(RegistrationSpec spec) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String activityInterfaceName = spec.className() + "Activity";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(HL_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(HL_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format("JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", simpleName(spec.inputType())));

    String outputConversion = outputIsRuntime ? "out" : "new HelperOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : Substitutor.format("import {{inputType}};\n",
            Map.of("inputType", spec.inputType()));
    String outputTypeImport =
        outputIsRuntime ? "" : Substitutor.format("import {{outputType}};\n",
            Map.of("outputType", spec.outputType()));

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return HelperDsl.helper(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.HelperDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {{DEFINITIONS_PACKAGE}};
        
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.HelperDefinition;
        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        import {{GENERATED_PACKAGE}}.{{activityInterfaceName}};
        {{jsonPayloadImport}}        import {{specPackage}}.{{specClass}};
        {{inputTypeImport}}        {{outputTypeImport}}
        {{dslImportsBlock}}        import java.util.function.Function;
        import javax.annotation.processing.Generated;
        
        /**
         * Generated HelperDefinition wrapper + Activity implementation for {{specClass}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.HelperCodeGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClass}} implements HelperDefinition, {{activityInterfaceName}} {
        
            private final {{specClass}} function;
        
            public {{wrapperClass}}() {
                this(null);
            }
        
            public {{wrapperClass}}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({{specClass}}.class) : new {{specClass}}();
            }
        
            @Override
            public String getCode() {
                return "{{code}}";
            }
        
            @Override
            public HelperOutput preview(HelperInput input) {
                {{inputTypeName}} typed = {{inputConversion}};
                {{outputTypeName}} out = function.preview(typed);
                return {{outputConversion}};
            }
        
            @Override
            public HelperOutput execute(HelperInput input) {
                {{inputTypeName}} typed = {{inputConversion}};
                {{outputTypeName}} out = function.execute(typed);
                return {{outputConversion}};
            }
        
            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """;

    String source = Substitutor.format(sourceTemplate, Map.ofEntries(
        Map.entry("DEFINITIONS_PACKAGE", DEFINITIONS_PACKAGE),
        Map.entry("GENERATED_PACKAGE", GENERATED_PACKAGE),
        Map.entry("activityInterfaceName", activityInterfaceName),
        Map.entry("jsonPayloadImport", jsonPayloadImport),
        Map.entry("specPackage", spec.packageName()),
        Map.entry("specClass", spec.className()),
        Map.entry("inputTypeImport", inputTypeImport),
        Map.entry("outputTypeImport", outputTypeImport),
        Map.entry("dslImportsBlock", dslImportsBlock),
        Map.entry("timestamp", timestamp),
        Map.entry("wrapperClass", wrapperClassName),
        Map.entry("code", spec.code()),
        Map.entry("inputTypeName", simpleName(spec.inputType())),
        Map.entry("inputConversion", inputConversion),
        Map.entry("outputTypeName", simpleName(spec.outputType())),
        Map.entry("outputConversion", outputConversion),
        Map.entry("dslBody", dslBodyOrFallback)));

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
