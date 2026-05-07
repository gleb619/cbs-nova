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
//TODO: remove file
@Deprecated(forRemoval = true)
public class ActivityRegistryGenerator {

  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";

  private final Filer filer;

  public void generate(
      List<RegistrationSpec> txSpecs,
      List<RegistrationSpec> helperSpecs,
      List<RegistrationSpec> conditionSpecs,
      List<EventWorkflowSpec> eventSpecs)
      throws IOException {
    if (txSpecs.isEmpty()
        && helperSpecs.isEmpty()
        && conditionSpecs.isEmpty()
        && eventSpecs.isEmpty()) {
      return;
    }

    String className = "GeneratedActivityRegistry";
    String fqcn = GENERATED_PACKAGE + "." + className;

    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    List<String> importLines = Stream.of(
            txSpecs.stream().map(spec -> toDefinitionImport(spec.className())),
            helperSpecs.stream().map(spec -> toDefinitionImport(spec.className())),
            conditionSpecs.stream().map(spec -> toDefinitionImport(spec.className())),
            eventSpecs.stream().map(spec -> toActivityImport(spec.eventCode())))
        .flatMap(s -> s)
        .toList();

    String imports = importLines.stream().distinct().collect(Collectors.joining("\n"));

    List<String> regLines = Stream.of(
            txSpecs.stream().map(spec -> toRegistration(spec.className() + "Definition")),
            helperSpecs.stream().map(spec -> toRegistration(spec.className() + "Definition")),
            conditionSpecs.stream().map(spec -> toRegistration(spec.className() + "Definition")),
            eventSpecs.stream().map(spec -> toActivityRegistration(spec.eventCode())))
        .flatMap(s -> s)
        .toList();

    String registrations = String.join("\n", regLines);

    String source = Substitutor.format( // language=java
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

  private static String toDefinitionImport(String className) {
    return Substitutor.format(
        "import {{package}}.definitions.{{className}};",
        Map.of("package", GENERATED_PACKAGE, "className", className + "Definition"));
  }

  private static String toActivityImport(String eventCode) {
    String className = toClassName(eventCode) + "EventActivityImpl";
    return Substitutor.format(
        "import {{package}}.{{className}};",
        Map.of("package", GENERATED_PACKAGE, "className", className));
  }

  private static String toRegistration(String className) {
    return Substitutor.format(
        "        worker.registerActivitiesImplementations(new {{className}}());",
        Map.of("className", className));
  }

  private static String toActivityRegistration(String eventCode) {
    String className = toClassName(eventCode) + "EventActivityImpl";
    return Substitutor.format(
        "        worker.registerActivitiesImplementations(new {{className}}Impl());",
        Map.of("className", className));
  }

  private static String toClassName(String code) {
    StringBuilder sb = new StringBuilder();
    boolean upper = true;
    for (char c : code.toCharArray()) {
      if (c == '_' || c == '-') {
        upper = true;
      } else if (upper) {
        sb.append(Character.toUpperCase(c));
        upper = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }
}
