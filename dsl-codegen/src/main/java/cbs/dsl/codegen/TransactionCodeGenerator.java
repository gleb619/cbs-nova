package cbs.dsl.codegen;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain-oriented generator for {@code @DslComponent(type = TRANSACTION)} components.
 *
 * <p>Produces two artifacts per component:
 *
 * <ol>
 *   <li>{@code {Code}Activity} — Temporal {@code @ActivityInterface} (thin contract, required by
 *       Temporal SDK).
 *   <li>{@code {Code}TransactionDefinition} — implements both {@code TransactionDefinition} and
 *       {@code {Code}Activity}. This is the single file that hosts the DSL metadata, the business
 *       delegation, and the Temporal activity contract.
 * </ol>
 */
@RequiredArgsConstructor
public class TransactionCodeGenerator {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";
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
            package {{package}};
            
            import cbs.dsl.api.TransactionTypes.TransactionInput;
            import cbs.dsl.api.TransactionTypes.TransactionOutput;
            import io.temporal.activity.ActivityInterface;
            import io.temporal.activity.ActivityMethod;
            import javax.annotation.processing.Generated;
            
            @Generated(
                value = "cbs.dsl.codegen.TransactionCodeGenerator",
                date = "{{timestamp}}"
            )
            @ActivityInterface
            public interface {{className}} {
            
                @ActivityMethod
                TransactionOutput execute(TransactionInput input);
            }
            """;
    String source = Substitutor.format(sourceTemplate, Map.of(
        "package", GENERATED_PACKAGE,
        "timestamp", timestamp,
        "className", className));

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
    boolean inputIsRuntime = spec.inputType().equals(TX_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(TX_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JsonPayload.fromMap(input.params(), " + simpleName(spec.inputType()) + ".class)";

    String outputConversion =
        outputIsRuntime ? "out" : "new TransactionOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport =
        outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return TransactionDsl.transaction(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.TransactionDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {{definitionsPackage}};
        
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.TransactionDefinition;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        import cbs.dsl.api.context.TransactionContext;
        import {{generatedPackage}}.{{activityInterfaceName}};
        {{jsonPayloadImport}}        import {{specPackageName}}.{{specClassName}};
        {{inputTypeImport}}{{outputTypeImport}}
        {{dslImportsBlock}}        import java.util.function.Consumer;
        import java.util.function.Function;
        import javax.annotation.processing.Generated;
        
        /**
         * Generated TransactionDefinition wrapper + Activity implementation for {{specClassName}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.TransactionCodeGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements TransactionDefinition, {{activityInterfaceName}} {
        
            private final {{specClassName}} function;
        
            public {{wrapperClassName}}() {
                this(null);
            }
        
            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({{specClassName}}.class) : new {{specClassName}}();
            }
        
            @Override
            public String getCode() {
                return "{{specCode}}";
            }
        
            @Override
            public TransactionOutput preview(TransactionInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.preview(typed);
                return {{outputConversion}};
            }
        
            @Override
            public TransactionOutput execute(TransactionInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.execute(typed);
                return {{outputConversion}};
            }
        
            @Override
            public TransactionOutput rollback(TransactionInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.rollback(typed);
                return {{outputConversion}};
            }
        
            @Override
            public DslObject dsl() {
                {{dslBodyOrFallback}}
            }
        }
        """;

    Map<String, String> params = new HashMap<>();
    params.put("definitionsPackage", DEFINITIONS_PACKAGE);
    params.put("generatedPackage", GENERATED_PACKAGE);
    params.put("activityInterfaceName", activityInterfaceName);
    params.put("jsonPayloadImport", jsonPayloadImport);
    params.put("specPackageName", spec.packageName());
    params.put("specClassName", spec.className());
    params.put("inputTypeImport", inputTypeImport);
    params.put("outputTypeImport", outputTypeImport);
    params.put("timestamp", timestamp);
    params.put("wrapperClassName", wrapperClassName);
    params.put("specCode", spec.code());
    params.put("inputSimpleName", simpleName(spec.inputType()));
    params.put("inputConversion", inputConversion);
    params.put("outputSimpleName", simpleName(spec.outputType()));
    params.put("outputConversion", outputConversion);
    params.put("dslImportsBlock", dslImportsBlock);
    params.put("dslBodyOrFallback", dslBodyOrFallback);
    String source = Substitutor.format(sourceTemplate, params);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
