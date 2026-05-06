package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.api.DslObject;
import cbs.dsl.builder.EventDslObject;
import cbs.dsl.builder.WorkflowDslObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class DslCodeGeneratorTest {

  private DslCodeGenerator generator;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    generator = new DslCodeGenerator();
  }

  @Test
  void generateWrapper_basic() {
    String result = generator.generateWrapper("MyWrapper", null, "System.out.println(\"hello\");");

    assertTrue(result.contains("public class MyWrapper"));
    assertTrue(result.contains("public static void main(String[] args)"));
    assertTrue(result.contains("System.out.println(\"hello\")"));
  }

  @Test
  void generateWrapper_withImports() {
    String imports = "import java.util.List;\nimport java.util.Map;";
    String body = "List.of(1, 2, 3);";
    String result = generator.generateWrapper("WithImports", imports, body);

    assertTrue(result.contains("import java.util.List"));
    assertTrue(result.contains("import java.util.Map"));
    assertTrue(result.contains("public class WithImports"));
  }

  @Test
  void substitutorFormat_basic() {
    String template = "Hello {{name}}!";
    Map<String, String> params = Map.of("name", "World");
    String result = Substitutor.format(template, params);
    assertEquals("Hello World!", result);
  }

  @Test
  void substitutorFormat_multiple() {
    String template = "{{greeting}} {{name}} at {{place}}";
    Map<String, String> params = Map.of(
        "greeting", "Hello",
        "name", "Alice",
        "place", "home"
    );
    String result = Substitutor.format(template, params);
    assertEquals("Hello Alice at home", result);
  }

  @Test
  void substitutorFormat_unknownKey() {
    String template = "Hello {{name}}!";
    Map<String, String> params = Map.of("other", "value");
    String result = Substitutor.format(template, params);
    assertEquals("Hello {{name}}!", result);
  }

  @Test
  void substitutorFormat_nullTemplate() {
    String result = Substitutor.format(null, Map.of("key", "value"));
    assertNull(result);
  }

  @Test
  void substitutorFormat_nullParams() {
    String template = "Hello {{name}}!";
    String result = Substitutor.format(template, null);
    assertEquals(template, result);
  }

  @Test
  void substitutorFormat_nestedBraces() {
    String template = "A{{x}}B{{y}}C";
    Map<String, String> params = Map.of("x", "1", "y", "2");
    String result = Substitutor.format(template, params);
    assertEquals("A1B2C", result);
  }

  @Test
  void generate_eventDefinition() throws Exception {
    EventDslObject event = new EventDslObject(
        "EV001",
        Collections.emptyList(),
        ctx -> {},
        ctx -> {},
        ctx -> {},
        Collections.emptyList(),
        (ctx, err) -> {}
    );

    generator.generate(event, tempDir);

    Path generatedDir = tempDir.resolve("cbs/dsl/generated");
    Path outputFile = generatedDir.resolve("EV001EventDefinition.java");
    
    // List files for debugging
    if (!Files.exists(outputFile)) {
      System.out.println("Temp dir: " + tempDir);
      System.out.println("Generated dir exists: " + Files.exists(generatedDir));
      if (Files.exists(generatedDir)) {
        Files.list(generatedDir).forEach(p -> System.out.println("  file: " + p.getFileName()));
      }
    }
    
    assertTrue(Files.exists(outputFile), "Output file should exist: " + outputFile);
    String content = Files.readString(outputFile);
    assertTrue(content.contains("public class EV001EventDefinition"), "Should contain class declaration");
    assertTrue(content.contains('"' + "EV001" + '"'), "Should contain code literal");
    assertTrue(content.contains("implements EventDefinition"), "Should implement EventDefinition");
  }

  @Test
  void generate_workflowDefinition() throws Exception {
    WorkflowDslObject workflow = new WorkflowDslObject(
        "WF001",
        List.of("PENDING", "ACTIVE", "COMPLETED"),
        "PENDING",
        List.of("COMPLETED"),
        Collections.emptyList()
    );

    generator.generate(workflow, tempDir);

    Path generatedDir = tempDir.resolve("cbs/dsl/generated");
    Path outputFile = generatedDir.resolve("WF001WorkflowDefinition.java");
    
    if (!Files.exists(outputFile)) {
      System.out.println("Temp dir: " + tempDir);
      System.out.println("Generated dir exists: " + Files.exists(generatedDir));
      if (Files.exists(generatedDir)) {
        Files.list(generatedDir).forEach(p -> System.out.println("  file: " + p.getFileName()));
      }
    }
    
    assertTrue(Files.exists(outputFile), "Output file should exist: " + outputFile);
    String content = Files.readString(outputFile);
    assertTrue(content.contains("public class WF001WorkflowDefinition"), "Should contain class declaration");
    assertTrue(content.contains('"' + "WF001" + '"'), "Should contain code literal");
    assertTrue(content.contains("implements WorkflowDefinition"), "Should implement WorkflowDefinition");
    assertTrue(content.contains("PENDING"), "Should contain PENDING state");
  }

  @Test
  void generate_withCustomDslBody() throws Exception {
    EventDslObject event = new EventDslObject(
        "EV002",
        Collections.emptyList(),
        ctx -> {},
        ctx -> {},
        ctx -> {},
        Collections.emptyList(),
        (ctx, err) -> {}
    );
    String customBody = "return EventDsl.event(\"EV002\").withTimeout(30).build();";
    String customImports = "import some.CustomImport;";

    generator.generate(event, customBody, customImports, tempDir);

    Path generatedDir = tempDir.resolve("cbs/dsl/generated");
    Path outputFile = generatedDir.resolve("EV002EventDefinition.java");
    
    if (!Files.exists(outputFile)) {
      System.out.println("Temp dir: " + tempDir);
      System.out.println("Generated dir exists: " + Files.exists(generatedDir));
      if (Files.exists(generatedDir)) {
        Files.list(generatedDir).forEach(p -> System.out.println("  file: " + p.getFileName()));
      }
    }
    
    assertTrue(Files.exists(outputFile), "Output file should exist: " + outputFile);
    String content = Files.readString(outputFile);
    assertTrue(content.contains("import some.CustomImport"), "Should contain custom import");
    assertTrue(content.contains("withTimeout(30)"), "Should contain custom body");
  }

  @Test
  void generate_workflowWithCustomDslBody() throws Exception {
    WorkflowDslObject workflow = new WorkflowDslObject(
        "WF002",
        List.of("A", "B"),
        "A",
        List.of("B"),
        Collections.emptyList()
    );
    String customBody = "return WorkflowDsl.workflow(\"WF002\").build();";
    String customImports = "import custom.Import;";

    generator.generate(workflow, customBody, customImports, tempDir);

    Path generatedDir = tempDir.resolve("cbs/dsl/generated");
    Path outputFile = generatedDir.resolve("WF002WorkflowDefinition.java");
    
    if (!Files.exists(outputFile)) {
      System.out.println("Temp dir: " + tempDir);
      System.out.println("Generated dir exists: " + Files.exists(generatedDir));
      if (Files.exists(generatedDir)) {
        Files.list(generatedDir).forEach(p -> System.out.println("  file: " + p.getFileName()));
      }
    }
    
    assertTrue(Files.exists(outputFile), "Output file should exist: " + outputFile);
    String content = Files.readString(outputFile);
    assertTrue(content.contains("import custom.Import"), "Should contain custom import");
    assertTrue(content.contains("WorkflowDsl.workflow"), "Should contain custom body");
  }
}
