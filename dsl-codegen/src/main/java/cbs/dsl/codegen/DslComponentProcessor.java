package cbs.dsl.codegen;

import cbs.dsl.api.ConditionFunction;
import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslComponent.DslComponentModel;
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
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

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

  private static final Function<RegistrationModel, String> UNDEFINED_DSL_BODY_PROVIDER =
      _ -> "return UndefinedDslObject.create();";

  private boolean processed = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (processed) return true;

    List<RegistrationModel> registrations = new ArrayList<>();

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
        List<RegistrationModel> txSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.TRANSACTION)
            .toList();
        List<RegistrationModel> helperSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.HELPER)
            .toList();
        List<RegistrationModel> eventSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.EVENT)
            .toList();
        List<RegistrationModel> workflowSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.WORKFLOW)
            .toList();
        List<RegistrationModel> conditionSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.CONDITION)
            .toList();
        List<RegistrationModel> massOpSpecs = registrations.stream()
            .filter(r -> r.interfaceType() == DslInterfaceType.MASS_OPERATION)
            .toList();

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
          Map<RegistrationModel, String> activitySources =
              Collections.synchronizedMap(new HashMap<>());
          Map<RegistrationModel, String> definitionSources =
              Collections.synchronizedMap(new HashMap<>());
          Map<RegistrationModel, String> genericSources =
              Collections.synchronizedMap(new HashMap<>());

          List<CompletableFuture<Void>> generationFutures = new ArrayList<>();

          for (RegistrationModel spec : txSpecs) {
            generationFutures.add(CompletableFuture.runAsync(
                () -> {
                  TransactionDefinitionGenerator gen =
                      new TransactionDefinitionGenerator(UNDEFINED_DSL_BODY_PROVIDER);
                  activitySources.put(spec, gen.generateActivityInterfaceCode(spec));
                  definitionSources.put(spec, gen.generateDefinitionCode(spec));
                },
                virtualExecutor));
          }
          for (RegistrationModel spec : helperSpecs) {
            generationFutures.add(CompletableFuture.runAsync(
                () -> {
                  HelperDefinitionGenerator gen = new HelperDefinitionGenerator(UNDEFINED_DSL_BODY_PROVIDER);
                  genericSources.put(spec, gen.generateDefinitionCode(spec));
                },
                virtualExecutor));
          }
          for (RegistrationModel spec : eventSpecs) {
            generationFutures.add(CompletableFuture.runAsync(
                () -> {
                  EventDefinitionGenerator gen = new EventDefinitionGenerator(UNDEFINED_DSL_BODY_PROVIDER);
                  activitySources.put(spec, gen.generateActivityInterfaceCode(spec));
                  definitionSources.put(spec, gen.generateDefinitionCode(spec));
                },
                virtualExecutor));
          }
          for (RegistrationModel spec : workflowSpecs) {
            generationFutures.add(CompletableFuture.runAsync(
                () -> {
                  WorkflowDefinitionGenerator gen =
                      new WorkflowDefinitionGenerator(UNDEFINED_DSL_BODY_PROVIDER);
                  genericSources.put(spec, gen.generateDefinitionCode(spec));
                },
                virtualExecutor));
          }
          for (RegistrationModel spec : conditionSpecs) {
            generationFutures.add(CompletableFuture.runAsync(
                () -> {
                  ConditionDefinitionGenerator gen =
                      new ConditionDefinitionGenerator(UNDEFINED_DSL_BODY_PROVIDER);
                  activitySources.put(spec, gen.generateActivityInterfaceCode(spec));
                  definitionSources.put(spec, gen.generateDefinitionCode(spec));
                },
                virtualExecutor));
          }
          for (RegistrationModel spec : massOpSpecs) {
            generationFutures.add(CompletableFuture.runAsync(
                () -> {
                  MassOperationDefinitionGenerator gen =
                      new MassOperationDefinitionGenerator(UNDEFINED_DSL_BODY_PROVIDER);
                  genericSources.put(spec, gen.generateDefinitionCode(spec));
                },
                virtualExecutor));
          }

          CompletableFuture.allOf(generationFutures.toArray(new CompletableFuture[0]))
              .join();

          Filer filer = processingEnv.getFiler();
          TransactionDefinitionGenerator txGen =
              new TransactionDefinitionGenerator(filer, UNDEFINED_DSL_BODY_PROVIDER);
          for (RegistrationModel spec : txSpecs) {
            txGen.writeActivityInterface(spec, activitySources.get(spec));
            txGen.writeDefinition(spec, definitionSources.get(spec));
          }
          for (RegistrationModel spec : helperSpecs) {
            new HelperDefinitionGenerator(filer, UNDEFINED_DSL_BODY_PROVIDER)
                .writeDefinition(spec, genericSources.get(spec));
          }
          EventDefinitionGenerator eventGen = new EventDefinitionGenerator(filer, UNDEFINED_DSL_BODY_PROVIDER);
          for (RegistrationModel spec : eventSpecs) {
            // TODO: we need to use
            // `dsl-codegen/src/main/java/cbs/dsl/codegen/EventSpecificationGenerator.java` instead
            eventGen.writeActivityInterface(spec, activitySources.get(spec));
            eventGen.writeDefinition(spec, definitionSources.get(spec));
          }
          for (RegistrationModel spec : workflowSpecs) {
            new WorkflowDefinitionGenerator(filer, UNDEFINED_DSL_BODY_PROVIDER)
                .writeDefinition(spec, genericSources.get(spec));
          }
          ConditionDefinitionGenerator conditionGen =
              new ConditionDefinitionGenerator(filer, UNDEFINED_DSL_BODY_PROVIDER);
          for (RegistrationModel spec : conditionSpecs) {
            conditionGen.writeActivityInterface(spec, activitySources.get(spec));
            conditionGen.writeDefinition(spec, definitionSources.get(spec));
          }
          for (RegistrationModel spec : massOpSpecs) {
            new MassOperationDefinitionGenerator(filer, UNDEFINED_DSL_BODY_PROVIDER)
                .writeDefinition(spec, genericSources.get(spec));
          }

          new DefinitionRegistryGenerator(filer).generate(registrations);

          List<EventSpecificationModel> eventSpecificationModels = eventSpecs.stream()
              .map(r ->
                  new EventSpecificationModel(r.code(), r.packageName() + "." + r.className(), List.of()))
              .toList();
          if (!eventSpecificationModels.isEmpty()) {
            new EventSpecificationGenerator(filer).generateAndWrite(eventSpecificationModels);
            
          }

          if (!txSpecs.isEmpty() || !conditionSpecs.isEmpty() || !eventSpecificationModels.isEmpty()) {
            new SpecificationRegistryGenerator(filer)
                .generate(txSpecs, conditionSpecs, eventSpecificationModels);
          }
        } finally {
          virtualExecutor.shutdown();
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

  private void validateAndCollect(TypeElement typeElement, List<RegistrationModel> registrations) {
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

    DslComponentModel componentModel = resolveComponentModel(annotation, typeElement);

    String dslBody = null;
    String dslImports = null;
    String sourceCode = null;
    try {
      FileObject sourceFile = processingEnv
          .getFiler()
          .getResource(StandardLocation.SOURCE_PATH, packageName, className + ".java");
      String content = sourceFile.getCharContent(true).toString();
      sourceCode = content;
      if (!DslCompiler.containsExplicitTypeDeclaration(content)) {
        DslCompiler.ParsedDsl parsed = DslCompiler.parseImplicitClassWithJavaParser(content);
        dslBody = parsed.body();
        dslImports = parsed.imports();
      }
    } catch (IOException e) {
      // Source file not available or not readable — ignore
    }

    registrations.add(new RegistrationModel(
        packageName,
        className,
        annotation.code(),
        interfaceType,
        inputType,
        outputType,
        componentModel,
        dslBody,
        dslImports,
        sourceCode));
  }

  /**
   * Resolves the component model for the given type. If the annotation specifies {@code AUTO},
   * inspect the class for any Spring annotation and return {@code SPRING} if found, otherwise
   * {@code SIMPLE}.
   */
  private DslComponentModel resolveComponentModel(
      DslComponent annotation, TypeElement typeElement) {
    if (annotation.componentModel() != DslComponentModel.AUTO) {
      return annotation.componentModel();
    }
    boolean hasSpringAnnotation = typeElement.getAnnotationMirrors().stream().anyMatch(a -> {
      String name = a.getAnnotationType().asElement().toString();
      return name.startsWith("org.springframework.");
    });
    return hasSpringAnnotation ? DslComponentModel.SPRING : DslComponentModel.SIMPLE;
  }
}
