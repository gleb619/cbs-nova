package cbs.dsl.codegen;

import java.util.stream.Stream;
import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ActivityRegistryGenerator {

  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";

  private final Filer filer;

  public ActivityRegistryGenerator(Filer filer) {
    this.filer = filer;
  }

  public void generate(List<RegistrationSpec> txSpecs, List<RegistrationSpec> helperSpecs)
      throws IOException {
    if (txSpecs.isEmpty() && helperSpecs.isEmpty()) {
      return;
    }

    String className = "GeneratedActivityRegistry";
    String fqcn = GENERATED_PACKAGE + "." + className;

    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String imports = allSpecs(txSpecs, helperSpecs).stream()
        .map(spec -> "import %s.%sActivityImpl;".formatted(GENERATED_PACKAGE, spec.className()))
        .collect(Collectors.joining("\n"));

    String registrations = allSpecs(txSpecs, helperSpecs).stream()
        .map(spec -> "        worker.registerActivitiesImplementations(new %sActivityImpl());"
            .formatted(spec.className()))
        .collect(Collectors.joining("\n"));

    String source = """
        package %s;

        import io.temporal.worker.Worker;
        %s

        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.ActivityRegistryGenerator",
            date = "%s"
        )
        public final class %s {

            private %s() {}

            public static void registerAll(Worker worker) {
        %s
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            imports.isBlank() ? "" : "\n" + imports,
            timestamp,
            className,
            className,
            registrations);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static List<RegistrationSpec> allSpecs(
      List<RegistrationSpec> txSpecs, List<RegistrationSpec> helperSpecs) {
    return Stream.concat(txSpecs.stream(), helperSpecs.stream())
        .toList();
  }
}
