package cbs.dsl.codegen;

import cbs.dsl.api.DslObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates Java source files using text block templates.
 *
 * <p>Two generation modes are supported:
 *
 * <ol>
 *   <li>Wrapper classes for JEP 512 implicit-class DSL files.
 *   <li>Definition implementations from collected {@link DslObject} instances.
 * </ol>
 */
public final class DslCodeGenerator {

  private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

  private static final String WRAPPER_TEMPLATE = //language=java
      """
      public class {{className}} {
          public static void main(String[] args) throws Exception {
      {{body}}
          }
      }
      """;

  private static final String EVENT_TEMPLATE = //language=java
      """
      package cbs.dsl.generated;

      import cbs.dsl.api.EventDefinition;
      import cbs.dsl.api.EventTypes.EventInput;
      import cbs.dsl.api.EventTypes.EventOutput;
      import cbs.dsl.builder.EventDsl;
      import java.util.Collections;

      public class {{className}} implements EventDefinition {

          @Override
          public String getCode() {
              return "{{code}}";
          }

          @Override
          public EventOutput execute(EventInput input) {
              return new EventOutput(Collections.emptyMap(), "SUCCESS");
          }

          @Override
          public DslObject dsl() {
      {{dslMethodBody}}
          }
      }
      """;

  private static final String TRANSACTION_TEMPLATE = //language=java
      """
      package cbs.dsl.generated;

      import cbs.dsl.api.TransactionDefinition;
      import cbs.dsl.api.TransactionTypes.TransactionInput;
      import cbs.dsl.api.TransactionTypes.TransactionOutput;
      import cbs.dsl.builder.TransactionDsl;

      public class {{className}} implements TransactionDefinition {

          @Override
          public String getCode() {
              return "{{code}}";
          }

          @Override
          public TransactionOutput execute(TransactionInput input) {
              return TransactionOutput.empty();
          }

          @Override
          public DslObject dsl() {
      {{dslMethodBody}}
          }
      }
      """;

  private static final String WORKFLOW_TEMPLATE = //language=java
      """
      package cbs.dsl.generated;

      import cbs.dsl.api.WorkflowDefinition;
      import cbs.dsl.api.WorkflowTypes.WorkflowInput;
      import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
      import cbs.dsl.api.TransitionRuleDefinition;
      import cbs.dsl.builder.WorkflowDsl;
      import java.util.Collections;
      import java.util.List;

      public class {{className}} implements WorkflowDefinition {

          @Override
          public String getCode() {
              return "{{code}}";
          }

          @Override
          public List<String> getStates() {
              return {{states}};
          }

          @Override
          public String getInitial() {
              return "{{initial}}";
          }

          @Override
          public List<String> getTerminalStates() {
              return {{terminalStates}};
          }

          @Override
          public List<TransitionRuleDefinition> getTransitions() {
              return Collections.emptyList();
          }

          @Override
          public WorkflowOutput execute(WorkflowInput input) {
              return new WorkflowOutput("DONE");
          }

          @Override
          public DslObject dsl() {
      {{dslMethodBody}}
          }
      }
      """;

  private static final String CONDITION_TEMPLATE = //language=java
      """
      package cbs.dsl.generated;

      import cbs.dsl.api.ConditionDefinition;
      import cbs.dsl.builder.ConditionDsl;

      public class {{className}} implements ConditionDefinition {

          @Override
          public String getCode() {
              return "{{code}}";
          }

          @Override
          public DslObject dsl() {
      {{dslMethodBody}}
          }
      }
      """;

  private static final String HELPER_TEMPLATE = //language=java
      """
      package cbs.dsl.generated;

      import cbs.dsl.api.HelperDefinition;
      import cbs.dsl.builder.HelperDsl;

      public class {{className}} implements HelperDefinition {

          @Override
          public String getCode() {
              return "{{code}}";
          }

          @Override
          public DslObject dsl() {
      {{dslMethodBody}}
          }
      }
      """;

  private static final String MASS_OPERATION_TEMPLATE = //language=java
      """
      package cbs.dsl.generated;

      import cbs.dsl.api.MassOperationDefinition;
      import cbs.dsl.api.MassOperationTypes.MassOperationInput;
      import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
      import cbs.dsl.builder.MassOperationDsl;

      public class {{className}} implements MassOperationDefinition {

          @Override
          public String getCode() {
              return "{{code}}";
          }

          @Override
          public MassOperationOutput execute(MassOperationInput input) {
              return new MassOperationOutput(0L, 0L, "SUCCESS");
          }

          @Override
          public DslObject dsl() {
      {{dslMethodBody}}
          }
      }
      """;

  /**
   * Generates a wrapper class that embeds DSL body code inside a {@code main(String[])} method.
   *
   * @param className the desired class name
   * @param importBlock original import statements (preserved verbatim)
   * @param body the DSL body statements
   * @return the complete Java source including imports and wrapper class
   */
  public String generateWrapper(String className, String importBlock, String body) {
    String source = Substitutor.format(WRAPPER_TEMPLATE, Map.of(
        "className", className,
        "body", indent(body, 8)
    ));

    if (importBlock != null && !importBlock.isBlank()) {
      return importBlock.trim() + "\n\n" + source;
    }
    return source;
  }

  /**
   * Generates a Java source file from a {@link DslObject} and writes it to {@code outputDir}.
   *
   * @param object the DSL object collected during compilation
   * @param outputDir the directory where the {@code .java} file will be written
   * @throws IOException if writing the file fails
   */
  public void generate(DslObject object, Path outputDir) throws IOException {
    generate(object, null, null, outputDir);
  }

  public void generate(DslObject object, String dslBody, String dslImports, Path outputDir)
      throws IOException {
    String source = buildSource(object, dslBody);

    if (dslImports != null && !dslImports.isBlank()) {
      int packageEnd = source.indexOf("\n\n", source.indexOf("package "));
      if (packageEnd > 0) {
        source = source.substring(0, packageEnd + 2) + dslImports.trim() + "\n\n"
            + source.substring(packageEnd + 2);
      }
    }

    Matcher matcher = CLASS_NAME_PATTERN.matcher(source);
    String className = matcher.find() ? matcher.group(1) : "GeneratedClass";

    Path outputPath = outputDir.resolve("cbs/dsl/generated").resolve(className + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }

  private String buildSource(DslObject object, String dslBody) {
    Class<?> enclosing = object.getClass().getEnclosingClass();
    String typeName = enclosing != null ? enclosing.getSimpleName() : "";
    String simpleName = object.getClass().getSimpleName();

    String dslMethodBody = buildDslMethodBody(object, dslBody);
    String code = object.getCode();

    String className = sanitizeClassName(code);
    String states = "java.util.Collections.emptyList()";
    String initial = "";
    String terminalStates = "java.util.Collections.emptyList()";

    String typeSuffix;
    String template;

    switch (typeName) {
      case "EventBuilder":
        typeSuffix = "EventDefinition";
        template = EVENT_TEMPLATE;
        break;
      case "TransactionBuilder":
        typeSuffix = "TransactionDefinition";
        template = TRANSACTION_TEMPLATE;
        break;
      case "WorkflowBuilder":
        typeSuffix = "WorkflowDefinition";
        template = WORKFLOW_TEMPLATE;
        List<String> wfStates = invokeList(object, "getStates");
        String wfInitial = invokeString(object, "getInitial");
        List<String> wfTerminalStates = invokeList(object, "getTerminalStates");
        states = listOfLiterals(wfStates);
        initial = wfInitial;
        terminalStates = listOfLiterals(wfTerminalStates);
        break;
      case "ConditionBuilder":
        typeSuffix = "ConditionDefinition";
        template = CONDITION_TEMPLATE;
        break;
      case "HelperBuilder":
        typeSuffix = "HelperDefinition";
        template = HELPER_TEMPLATE;
        break;
      case "MassOperationBuilder":
        typeSuffix = "MassOperationDefinition";
        template = MASS_OPERATION_TEMPLATE;
        break;
      default:
        switch (simpleName) {
          case "EventDslObject":
            typeSuffix = "EventDefinition";
            template = EVENT_TEMPLATE;
            break;
          case "TransactionDslObject":
            typeSuffix = "TransactionDefinition";
            template = TRANSACTION_TEMPLATE;
            break;
          case "WorkflowDslObject":
            typeSuffix = "WorkflowDefinition";
            template = WORKFLOW_TEMPLATE;
            List<String> wfdStates = invokeList(object, "getStates");
            String wfdInitial = invokeString(object, "getInitial");
            List<String> wfdTerminalStates = invokeList(object, "getTerminalStates");
            states = listOfLiterals(wfdStates);
            initial = wfdInitial;
            terminalStates = listOfLiterals(wfdTerminalStates);
            break;
          case "ConditionDslObject":
            typeSuffix = "ConditionDefinition";
            template = CONDITION_TEMPLATE;
            break;
          case "HelperDslObject":
            typeSuffix = "HelperDefinition";
            template = HELPER_TEMPLATE;
            break;
          case "MassOperationDslObject":
            typeSuffix = "MassOperationDefinition";
            template = MASS_OPERATION_TEMPLATE;
            break;
          default:
            throw new IllegalArgumentException(
                "Unsupported builder type: %s / %s".formatted(typeName, simpleName));
        }
    }

    Map<String, String> params = Map.of(
        "className", className + typeSuffix,
        "code", code,
        "dslMethodBody", dslMethodBody,
        "states", states,
        "initial", initial,
        "terminalStates", terminalStates
    );

    return Substitutor.format(template, params);
  }

  private String buildDslMethodBody(DslObject object, String dslBody) {
    if (dslBody != null && !dslBody.isBlank()) {
      return indent(dslBody.trim(), 14) + "\n";
    }

    Class<?> enclosing = object.getClass().getEnclosingClass();
    String typeName = enclosing != null ? enclosing.getSimpleName() : "";
    String simpleName = object.getClass().getSimpleName();
    String code = object.getCode();

    String dslBuilder = switch (typeName) {
      case "EventBuilder" -> "EventDsl";
      case "TransactionBuilder" -> "TransactionDsl";
      case "WorkflowBuilder" -> "WorkflowDsl";
      case "ConditionBuilder" -> "ConditionDsl";
      case "HelperBuilder" -> "HelperDsl";
      case "MassOperationBuilder" -> "MassOperationDsl";
      default ->
        switch (simpleName) {
          case "EventDslObject" -> "EventDsl";
          case "TransactionDslObject" -> "TransactionDsl";
          case "WorkflowDslObject" -> "WorkflowDsl";
          case "ConditionDslObject" -> "ConditionDsl";
          case "HelperDslObject" -> "HelperDsl";
          case "MassOperationDslObject" -> "MassOperationDsl";
          default -> throw new IllegalArgumentException("Unsupported type: %s / %s".formatted(typeName, simpleName));
        };
    };

    String body = String.format("return %s.%s(\"%s\").build();", dslBuilder,
        Character.toLowerCase(dslBuilder.charAt(0)) + dslBuilder.substring(1), code);
    return indent(body, 14) + "\n";
  }

  private static String listOfLiterals(List<String> items) {
    if (items == null || items.isEmpty()) {
      return "java.util.Collections.emptyList()";
    }
    return "java.util.List.of(%s)".formatted(
        items.stream().map("\"%s\""::formatted).collect(Collectors.joining(", ")));
  }

  @SuppressWarnings("unchecked")
  private static List<String> invokeList(DslObject obj, String methodName) {
    try {
      Method method = obj.getClass().getDeclaredMethod(methodName);
      Object result = method.invoke(obj);
      if (result instanceof List<?> list) {
        return list.stream().map(Object::toString).collect(Collectors.toList());
      }
    } catch (Exception e) {
      // ignore
    }
    return Collections.emptyList();
  }

  private static String invokeString(DslObject obj, String methodName) {
    try {
      Method method = obj.getClass().getDeclaredMethod(methodName);
      Object result = method.invoke(obj);
      return result != null ? result.toString() : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static String sanitizeClassName(String input) {
    if (input == null || input.isEmpty()) {
      return "GeneratedClass";
    }
    String clean = input.replaceAll("[^a-zA-Z0-9_]", "_");
    if (clean.isEmpty()) {
      clean = "GeneratedClass";
    }
    String first = clean.substring(0, 1).toUpperCase();
    String rest = clean.length() > 1 ? clean.substring(1) : "";
    return first + rest;
  }

  private static String indent(String text, int spaces) {
    String pad = " ".repeat(spaces);
    return Stream.of(text.split("\n"))
        .map(line -> pad + line)
        .collect(Collectors.joining("\n"));
  }
}
