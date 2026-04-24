package cbs.dsl.codegen;

import cbs.dsl.api.DslComponent;

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

  private static final Set<String> ALLOWED_INTERFACES = Set.of(
      "cbs.dsl.api.TransactionDefinition",
      "cbs.dsl.api.HelperDefinition",
      "cbs.dsl.api.ConditionDefinition",
      "cbs.dsl.api.WorkflowDefinition",
      "cbs.dsl.api.MassOperationDefinition");

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

    if (!registrations.isEmpty()) {
      try {
        new RegistrationGenerator(processingEnv.getFiler()).generate(registrations);
        processed = true;
      } catch (IOException e) {
        processingEnv
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Code generation failed: " + e.getMessage());
      }
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
              "Class '" + className
                  + "' annotated with @DslComponent must have a public no-arg constructor",
              typeElement);
      return;
    }

    // Find implemented allowed interface
    List<String> implementedAllowed = new ArrayList<>();
    for (TypeMirror iface : typeElement.getInterfaces()) {
      String ifaceName = ((DeclaredType) iface).asElement().toString();
      if (ALLOWED_INTERFACES.contains(ifaceName)) {
        implementedAllowed.add(ifaceName);
      }
    }

    if (implementedAllowed.isEmpty()) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Class '" + className + "' must implement exactly one of: " + ALLOWED_INTERFACES,
              typeElement);
      return;
    }

    if (implementedAllowed.size() > 1) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Class '" + className
                  + "' implements multiple DSL definition interfaces; must implement exactly one",
              typeElement);
      return;
    }

    DslComponent annotation = typeElement.getAnnotation(DslComponent.class);
    if (annotation.code().isBlank()) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@DslComponent.code must not be blank for class '" + className + "'",
              typeElement);
      return;
    }

    String implementedInterface = implementedAllowed.get(0);
    DslInterfaceType interfaceType =
        switch (implementedInterface) {
          case "cbs.dsl.api.TransactionDefinition" -> DslInterfaceType.TRANSACTION;
          case "cbs.dsl.api.HelperDefinition" -> DslInterfaceType.HELPER;
          case "cbs.dsl.api.ConditionDefinition" -> DslInterfaceType.CONDITION;
          case "cbs.dsl.api.WorkflowDefinition" -> DslInterfaceType.WORKFLOW;
          case "cbs.dsl.api.MassOperationDefinition" -> DslInterfaceType.MASS_OPERATION;
          default ->
            throw new IllegalStateException("Unsupported interface: " + implementedInterface);
        };

    registrations.add(
        new RegistrationSpec(packageName, className, annotation.code(), interfaceType));
  }
}
