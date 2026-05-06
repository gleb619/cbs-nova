package cbs.dsl.codegen;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Domain-oriented generator for {@code @DslComponent(type = CONDITION)} components.
 *
 * <p>Produces a single artifact per component: {@code {Code}ConditionDefinition} — implements
 * {@code ConditionDefinition} and hosts {@code dsl()} returning a {@code DslObject}.
 */
@RequiredArgsConstructor
public class ConditionCodeGenerator {

  private static final String CN_INPUT = "cbs.dsl.api.ConditionTypes.ConditionInput";
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
    boolean inputIsRuntime = spec.inputType().equals(CN_INPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format("JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", simpleName(spec.inputType())));

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : Substitutor.format("import {{inputType}};\n",
            Map.of("inputType", spec.inputType()));

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return ConditionDsl.condition(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.ConditionDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {{DEFINITIONS_PACKAGE}};
        
        import cbs.dsl.api.ConditionDefinition;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.context.TransactionContext;
        {{jsonPayloadImport}}        import {{specPackage}}.{{specClass}};
        {{inputTypeImport}}        import {{outputType}};
        {{dslImportsBlock}}        import java.util.function.Predicate;
        import javax.annotation.processing.Generated;
        
        /**
         * Generated ConditionDefinition wrapper for {{specClass}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.ConditionCodeGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClass}} implements ConditionDefinition {
        
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
            public Predicate<TransactionContext> getPredicate() {
                return ctx -> false;
            }
        
            @Override
            public ConditionOutput evaluate(ConditionInput input) {
                {{inputTypeName}} typed = {{inputConversion}};
                {{outputTypeName}} out = function.evaluate(typed);
                return new ConditionOutput(out.getValue());
            }
        
            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """;

    String source = Substitutor.format(sourceTemplate, Map.ofEntries(
        Map.entry("DEFINITIONS_PACKAGE", DEFINITIONS_PACKAGE),
        Map.entry("jsonPayloadImport", jsonPayloadImport),
        Map.entry("specPackage", spec.packageName()),
        Map.entry("specClass", spec.className()),
        Map.entry("inputTypeImport", inputTypeImport),
        Map.entry("outputType", spec.outputType()),
        Map.entry("dslImportsBlock", dslImportsBlock),
        Map.entry("timestamp", timestamp),
        Map.entry("wrapperClass", wrapperClassName),
        Map.entry("code", spec.code()),
        Map.entry("inputTypeName", simpleName(spec.inputType())),
        Map.entry("inputConversion", inputConversion),
        Map.entry("outputTypeName", simpleName(spec.outputType())),
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
