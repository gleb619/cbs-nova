package cbs.dsl.codegen;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class ActivityRegistryGenerator {

  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";

  private final Filer filer;

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
        .map(spec -> MessageFormat.format(
            "import {0}.definitions.{1}Definition;", GENERATED_PACKAGE, spec.className()))
        .collect(Collectors.joining("\n"));

    String registrations = allSpecs(txSpecs, helperSpecs).stream()
        .map(spec -> MessageFormat.format(
            "        worker.registerActivitiesImplementations(new {0}Definition());",
            spec.className()))
        .collect(Collectors.joining("\n"));

    String source = MessageFormat.format(
        """
        package {0};

        import io.temporal.worker.Worker;
        {1}

        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.ActivityRegistryGenerator",
            date = "{2}"
        )
        public final class {3} {

            private {3}() {}

            public static void registerAll(Worker worker) {
        {4}
            }
        }
        """,
        GENERATED_PACKAGE,
        imports.isBlank() ? "" : "\n" + imports,
        timestamp,
        className,
        registrations);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static List<RegistrationSpec> allSpecs(
      List<RegistrationSpec> txSpecs, List<RegistrationSpec> helperSpecs) {
    return Stream.concat(txSpecs.stream(), helperSpecs.stream()).toList();
  }
}
