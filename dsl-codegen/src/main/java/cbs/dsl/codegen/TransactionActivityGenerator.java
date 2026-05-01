package cbs.dsl.codegen;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransactionActivityGenerator {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";

  private final Filer filer;

  public TransactionActivityGenerator(Filer filer) {
    this.filer = filer;
  }

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      generateInterface(spec);
      generateImplementation(spec);
    }
  }

  private void generateInterface(RegistrationSpec spec) throws IOException {
    String className = spec.className() + "Activity";
    String fqcn = GENERATED_PACKAGE + "." + className;

    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String source = """
        package %s;

        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        import io.temporal.activity.ActivityInterface;
        import io.temporal.activity.ActivityMethod;

        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.TransactionActivityGenerator",
            date = "%s"
        )
        @ActivityInterface
        public interface %s {

            @ActivityMethod
            TransactionOutput execute(TransactionInput input);
        }
        """.formatted(GENERATED_PACKAGE, timestamp, className);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private void generateImplementation(RegistrationSpec spec) throws IOException {
    String interfaceName = spec.className() + "Activity";
    String className = spec.className() + "ActivityImpl";
    String fqcn = GENERATED_PACKAGE + "." + className;

    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    boolean inputIsRuntime = spec.inputType().equals(TX_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(TX_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String outputConversion =
        outputIsRuntime ? "out" : "new TransactionOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport = outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    String source = """
        package %s;

        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        %s        import %s.%s;
        %s%s
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.TransactionActivityGenerator",
            date = "%s"
        )
        public class %s implements %s {

            private final %s function = new %s();

            @Override
            public TransactionOutput execute(TransactionInput input) {
                %s typed = %s;
                %s out = function.execute(typed);
                return %s;
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            outputTypeImport,
            timestamp,
            className,
            interfaceName,
            spec.className(),
            spec.className(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
