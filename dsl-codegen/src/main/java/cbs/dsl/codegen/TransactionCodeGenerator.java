package cbs.dsl.codegen;

import cbs.dsl.codegen.DslCompiler.FileWrite;
import java.util.ArrayList;
import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
public class TransactionCodeGenerator {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Function<RegistrationSpec, String> dslBodyProvider;

  public TransactionCodeGenerator(Function<RegistrationSpec, String> dslBodyProvider) {
    this(null, dslBodyProvider);
  }

  public TransactionCodeGenerator(Filer filer, Function<RegistrationSpec, String> dslBodyProvider) {
    this.filer = filer;
    this.dslBodyProvider = dslBodyProvider;
  }

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      String activitySource = generateActivityInterfaceCode(spec);
      writeActivityInterface(spec, activitySource);

      String definitionSource = generateDefinitionCode(spec);
      writeDefinition(spec, definitionSource);
    }
  }

  public String generateActivityInterfaceCode(RegistrationSpec spec) {
    String className = spec.className() + "Activity";
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    return Substitutor.format(
        // language=java
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
            ContextOutput prepare(Map<String, Object> params);

            @ActivityMethod
            TransactionOutput execute(TransactionInput input);
        
        }
        """,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "className", className));
  }

  public void writeActivityInterface(RegistrationSpec spec, String source) throws IOException {
    String className = spec.className() + "Activity";
    String fqcn = GENERATED_PACKAGE + "." + className;
    JavaFileObject file = filer.createSourceFile(fqcn);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  public String generateDefinitionCode(RegistrationSpec spec) {
    String wrapperClassName = spec.className() + "Definition";
    String activityInterfaceName = spec.className() + "Activity";

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(TX_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(TX_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", simpleName(spec.inputType())));

    String outputConversion =
        outputIsRuntime ? "out" : "new TransactionOutput(JsonPayload.params(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import %s;\n".formatted(spec.inputType());
    String outputTypeImport = outputIsRuntime ? "" : "import %s;\n".formatted(spec.outputType());

    boolean hasCustomInput = !inputIsRuntime;
    String bigDecimalImport = hasCustomInput ? "import java.math.BigDecimal;\n" : "";

    String dslBody = dslBodyProvider.apply(spec);
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : "import cbs.dsl.builder.UndefinedDslObject;\n";

    return Substitutor.format(
        // language=java
        """
        package {{definitionsPackage}};

        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.ParameterDefinition;
        import cbs.dsl.api.TransactionDefinition;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        import cbs.dsl.api.ParametersTypes.ParameterError;
        import cbs.dsl.api.ParametersTypes.ParametersInput;
        import cbs.dsl.api.ParametersTypes;
        import {{generatedPackage}}.{{activityInterfaceName}};
        {{jsonPayloadImport}}{{bigDecimalImport}}        import {{specPackageName}}.{{specClassName}};
        {{inputTypeImport}}{{outputTypeImport}}
        {{dslImportsBlock}}        import java.util.ArrayList;
        import java.util.Collections;
        import java.util.List;
        import java.util.function.Consumer;
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
            public List<ParameterError> validateParameters(ParametersInput input) {
                List<ParameterError> errors = new ArrayList<>();
                for (ParameterDefinition param : getParameters()) {
                    ParameterError error = ParametersTypes.validate(param, input);
                    if (error != null) {
                        errors.add(error);
                    }
                }
                return errors;
            }
        
            @Override
            public ContextOutput prepare(Map<String, Object> params) {
                return prepareContext(input.params());
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
                {{dslBody}}
            }
        }
        """,
        Map.ofEntries(
            Map.entry("definitionsPackage", DEFINITIONS_PACKAGE),
            Map.entry("generatedPackage", GENERATED_PACKAGE),
            Map.entry("activityInterfaceName", activityInterfaceName),
            Map.entry("jsonPayloadImport", jsonPayloadImport),
            Map.entry("specPackageName", spec.packageName()),
            Map.entry("specClassName", spec.className()),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputTypeImport", outputTypeImport),
            Map.entry("timestamp", timestamp),
            Map.entry("wrapperClassName", wrapperClassName),
            Map.entry("specCode", spec.code()),
            Map.entry("inputSimpleName", simpleName(spec.inputType())),
            Map.entry("inputConversion", inputConversion),
            Map.entry("outputSimpleName", simpleName(spec.outputType())),
            Map.entry("outputConversion", outputConversion),
            Map.entry("dslImportsBlock", dslImportsBlock),
            Map.entry("dslBody", dslBody),
            Map.entry("bigDecimalImport", bigDecimalImport)));
  }

  public List<FileWrite> generateFileSpecs(RegistrationSpec spec, Path outputDir) {
    List<FileWrite> files = new ArrayList<>();
    String activitySource = generateActivityInterfaceCode(spec);
    files.add(writeActivityInterfaceToSpec(spec, activitySource, outputDir));
    String definitionSource = generateDefinitionCode(spec);
    files.add(writeDefinitionToSpec(spec, definitionSource, outputDir));
    return files;
  }

  private FileWrite writeActivityInterfaceToSpec(
      RegistrationSpec spec, String source, Path outputDir) {
    String className = spec.className() + "Activity";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    return new FileWrite(outputPath, source);
  }

  private FileWrite writeDefinitionToSpec(
      RegistrationSpec spec, String source, Path outputDir) {
    String wrapperClassName = spec.className() + "Definition";
    Path outputPath = outputDir
        .resolve("cbs/dsl/codegen/generated/definitions")
        .resolve(wrapperClassName + ".java");
    return new FileWrite(outputPath, source);
  }

  public void writeDefinition(RegistrationSpec spec, String source) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}