package cbs.dsl.codegen;

import cbs.dsl.api.ConditionFunction;
import cbs.dsl.api.DslComponent;
import cbs.dsl.api.EventFunction;
import cbs.dsl.api.HelperFunction;
import cbs.dsl.api.MassOperationFunction;
import cbs.dsl.api.TransactionFunction;
import cbs.dsl.api.WorkflowFunction;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import java.io.IOException;
import java.util.*;

@SupportedAnnotationTypes("cbs.dsl.api.DslComponent")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class DslComponentProcessor extends AbstractProcessor {

  private static final Map<String, DslInterfaceType> INTERFACE_TYPE_MAP = Map.of(
      TransactionFunction.class.getName(), DslInterfaceType.TRANSACTION,
      HelperFunction.class.getName(), DslInterfaceType.HELPER,
      ConditionFunction.class.getName(), DslInterfaceType.CONDITION,
      EventFunction.class.getName(), DslInterfaceType.EVENT,
      WorkflowFunction.class.getName(), DslInterfaceType.WORKFLOW,
      MassOperationFunction.class.getName(), DslInterfaceType.MASS_OPERATION);

  private boolean processed = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (processed) return true;

    List<RegistrationSpec> registrations = new ArrayList<>();

    for (Element element : roundEnv.getElementsAnnotatedWith(DslComponent.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Element annotated with @DslComponent must be a class",
                element);
        continue;
      }
      TypeElement typeElement = (TypeElement) element;
      validateAndCollect(typeElement, registrations);
    }

    try {
      if (!registrations.isEmpty()) {
        new DefinitionWrapperGenerator(processingEnv.getFiler()).generate(registrations);
        new RegistrationGenerator(processingEnv.getFiler()).generate(registrations);

        // Layer 3a: Generate Temporal workflow interfaces + implementations for EVENT types
        List<EventWorkflowSpec> eventSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.EVENT)
            .map(r ->
                new EventWorkflowSpec(r.code(), r.packageName() + "." + r.className(), List.of()))
            .toList();
        if (!eventSpecs.isEmpty()) {
          new EventWorkflowGenerator(processingEnv.getFiler()).generate(eventSpecs);
          new WorkflowRegistryGenerator(processingEnv.getFiler()).generate(eventSpecs);
        }

        // Layer 3b: Generate Temporal activities for TRANSACTION types
        List<RegistrationSpec> txSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.TRANSACTION)
            .toList();
        if (!txSpecs.isEmpty()) {
          new TransactionActivityGenerator(processingEnv.getFiler()).generate(txSpecs);
        }

        // Layer 3c: Generate Temporal activities for HELPER types
        List<RegistrationSpec> helperSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.HELPER)
            .toList();
        if (!helperSpecs.isEmpty()) {
          new HelperActivityGenerator(processingEnv.getFiler()).generate(helperSpecs);
        }

        // Layer 3d: Generate activity registry if any activities exist
        if (!txSpecs.isEmpty() || !helperSpecs.isEmpty()) {
          new ActivityRegistryGenerator(processingEnv.getFiler()).generate(txSpecs, helperSpecs);
        }
      }
      processed = true;
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Code generation failed: " + e.getMessage());
    }

    return true;
  }

  private void validateAndCollect(TypeElement typeElement, List<RegistrationSpec> registrations) {
    String className = typeElement.getSimpleName().toString();
    String packageName = processingEnv
        .getElementUtils()
        .getPackageOf(typeElement)
        .getQualifiedName()
        .toString();

    // Must have a no-arg constructor
    boolean hasNoArgConstructor = typeElement.getEnclosedElements().stream()
        .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
        .map(e -> (ExecutableElement) e)
        .anyMatch(c -> c.getParameters().isEmpty());

    if (!hasNoArgConstructor) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Class '%s' annotated with @DslComponent must have a public no-arg constructor"
                  .formatted(className),
              typeElement);
      return;
    }

    // Find implemented allowed interface
    List<DeclaredType> implementedAllowed = new ArrayList<>();
    for (TypeMirror iface : typeElement.getInterfaces()) {
      DeclaredType declaredType = (DeclaredType) iface;
      String ifaceName = declaredType.asElement().toString();
      if (INTERFACE_TYPE_MAP.containsKey(ifaceName)) {
        implementedAllowed.add(declaredType);
      }
    }

    if (implementedAllowed.isEmpty()) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Class '%s' must implement exactly one of: %s"
                  .formatted(className, INTERFACE_TYPE_MAP.keySet()),
              typeElement);
      return;
    }

    if (implementedAllowed.size() > 1) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Class '%s' implements multiple DSL function interfaces; must implement exactly one"
                  .formatted(className),
              typeElement);
      return;
    }

    DslComponent annotation = typeElement.getAnnotation(DslComponent.class);
    if (annotation.code().isBlank()) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@DslComponent.code must not be blank for class '%s'".formatted(className),
              typeElement);
      return;
    }

    DeclaredType implementedInterface = implementedAllowed.get(0);
    DslInterfaceType interfaceType =
        INTERFACE_TYPE_MAP.get(implementedInterface.asElement().toString());
    if (interfaceType == null) {
      throw new IllegalStateException("Unsupported interface: " + implementedInterface);
    }

    List<? extends TypeMirror> typeArgs = implementedInterface.getTypeArguments();
    if (typeArgs.size() != 2) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Class '%s' must declare exactly two type arguments for %s"
                  .formatted(className, implementedInterface),
              typeElement);
      return;
    }

    String inputType = typeArgs.get(0).toString();
    String outputType = typeArgs.get(1).toString();

    registrations.add(new RegistrationSpec(
        packageName, className, annotation.code(), interfaceType, inputType, outputType));
  }
}
