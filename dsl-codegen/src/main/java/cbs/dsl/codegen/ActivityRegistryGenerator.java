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
        .map(spec -> Substitutor.format(
            "import {{package}}.definitions.{{className}}Definition;",
            Map.of("package", GENERATED_PACKAGE, "className", spec.className())))
        .collect(Collectors.joining("\n"));

    String registrations = allSpecs(txSpecs, helperSpecs).stream()
        .map(spec -> Substitutor.format(
            "        worker.registerActivitiesImplementations(new {{className}}Definition());",
            Map.of("className", spec.className())))
        .collect(Collectors.joining("\n"));

    String source = Substitutor.format(//language=java
        """
        package {{GENERATED_PACKAGE}};
        
        import io.temporal.worker.Worker;
        import javax.annotation.processing.Generated;
        {{imports}}
        
        @Generated(
            value = "cbs.dsl.codegen.ActivityRegistryGenerator",
            date = "{{timestamp}}"
        )
        public final class {{className}} {
        
            private {{className}}() {}
        
            public static void registerAll(Worker worker) {
        {{registrations}}
            }
        }
        """,
        Map.ofEntries(
            Map.entry("GENERATED_PACKAGE", GENERATED_PACKAGE),
            Map.entry("imports", imports.isBlank() ? "" : "\n" + imports),
            Map.entry("timestamp", timestamp),
            Map.entry("className", className),
            Map.entry("registrations", registrations)));

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static List<RegistrationSpec> allSpecs(
      List<RegistrationSpec> txSpecs, List<RegistrationSpec> helperSpecs) {
    return Stream.concat(txSpecs.stream(), helperSpecs.stream()).toList();
  }
}
