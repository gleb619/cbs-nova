package cbs.nova.misc.codegen;

import cbs.nova.dsl.annotation.Helper;
import cbs.nova.dsl.annotation.Helper.ComponentModel;
import cbs.nova.dsl.annotation.Helper.CreationStrategy;
import cbs.nova.dsl.utils.Substitutor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
    "cbs.nova.dsl.annotation.Helper",
    "cbs.nova.starter.annotation.SpringHelper"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class HelperSpiProcessor extends AbstractProcessor {

  private static final String RESOLVER_CLASS = "GeneratedHelperResolver";
  private static final String INSTANCE_RESOLVER_CLASS = "GeneratedHelperInstanceResolver";
  private static final String HELPER_ANNOTATION = "cbs.nova.dsl.annotation.Helper";
  private static final String SPRING_HELPER_ANNOTATION = "cbs.nova.starter.annotation.SpringHelper";

  private final List<HelperEntry> entries = new ArrayList<>();
  private final AtomicBoolean wrote = new AtomicBoolean(false);

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      if (!wrote.get() && !entries.isEmpty()) {
        writeGeneratedHelperResolver();
        writeGeneratedHelperInstanceResolver();
        writeResolverServiceFile();
        writeInstanceResolverServiceFile();
        wrote.set(true);
      }
      return false;
    }

    for (var annotation : annotations) {
      var annotationName = annotation.getQualifiedName().toString();
      for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.CLASS) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                  "@Helper on non-class element ignored: " + element, element);
          continue;
        }
        var typeElement = (TypeElement) element;
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                  "@Helper on abstract class ignored: " + typeElement.getQualifiedName(), element);
          continue;
        }
        var fqn = typeElement.getQualifiedName().toString();
        if (!fqn.contains(".")) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                  "@Helper on default-package class ignored (cannot reference from generated SPI): "
                          + fqn,
                  element);
          continue;
        }
        var config = readHelperConfig(typeElement, annotationName);
        if (config == null) {
          continue;
        }
        entries.add(new HelperEntry(fqn, config, hasNoArgConstructor(typeElement)));
      }
    }
    return false;
  }

  /**
   * Reads the effective helper configuration from the element. For {@code @Helper} we use the
   * annotation directly. For {@code @SpringHelper} we extract the user-supplied {@code name()} from
   * {@code @SpringHelper} and force {@code componentModel=LAZY} and
   * {@code creationStrategy=STANDARD}, so the helper becomes a real Spring bean and is created via
   * the supplied {@code HelperInstanceResolver} (Spring) rather than via a generated
   * {@code new X()}.
   */
  private HelperConfig readHelperConfig(TypeElement element, String annotationName) {
    if (SPRING_HELPER_ANNOTATION.equals(annotationName)) {
      String name = readAnnotationStringValue(element, SPRING_HELPER_ANNOTATION, "name");
      if (name == null || name.isBlank()) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                "@SpringHelper without name() ignored: " + element.getQualifiedName(), element);
        return null;
      }
      return new HelperConfig(name, ComponentModel.LAZY, CreationStrategy.STANDARD);
    }
    if (HELPER_ANNOTATION.equals(annotationName)) {
      var helper = element.getAnnotation(Helper.class);
      if (helper == null) {
        return null;
      }
      return new HelperConfig(helper.name(), helper.componentModel(), helper.creationStrategy());
    }
    return null;
  }

  private String readAnnotationStringValue(Element element, String annotationFqn,
          String attribute) {
    for (var mirror : element.getAnnotationMirrors()) {
      if (!mirror.getAnnotationType().toString().equals(annotationFqn)) {
        continue;
      }
      for (var entry : mirror.getElementValues().entrySet()) {
        if (entry.getKey().getSimpleName().contentEquals(attribute)) {
          var value = entry.getValue().getValue();
          if (value instanceof String s) {
            return s;
          }
        }
      }
    }
    return null;
  }

  private void writeGeneratedHelperResolver() {
    var resolverPackage = commonPackage(entries);
    var resolverFqn = resolverPackage.isEmpty()
            ? RESOLVER_CLASS
            : resolverPackage + "." + RESOLVER_CLASS;
    try {
      var sourceFile = processingEnv.getFiler().createSourceFile(resolverFqn);
      try (var writer = new PrintWriter(sourceFile.openWriter())) {
        var imports = entries.stream()
                .filter(entry -> !packageOf(entry.fqn()).equals(resolverPackage))
                .map(entry -> "import " + entry.fqn() + ";\n")
                .collect(Collectors.joining());
        var registrations = entries.stream()
                .map(entry -> renderRegistration(entry))
                .collect(Collectors.joining());
        var packageLine = resolverPackage.isEmpty()
                ? ""
                : "package %s;\n\n".formatted(resolverPackage);
        var template = // language=java
                """
                        ${packageLine}import cbs.nova.dsl.helper.HelperInstanceResolver;
                        import cbs.nova.dsl.helper.HelperRegistrar;
                        import cbs.nova.dsl.helper.HelperResolver;
                                                ${imports}

                        public final class ${resolverClass} implements HelperResolver {
                          @Override
                          public void registerHelpers(HelperRegistrar registrar, HelperInstanceResolver instanceResolver) {
                        ${registrations}  }

                        }
                        """;
        writer.print(Substitutor.format(template, Map.of(
                "packageLine", packageLine,
                "imports", imports,
                "resolverClass", RESOLVER_CLASS,
                "registrations", registrations)));
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Failed to write GeneratedHelperResolver: " + e.getMessage());
    }
  }

  private String renderRegistration(HelperEntry entry) {
    var simpleName = simpleNameOf(entry.fqn());
    var name = entry.config().name();
    var lazy = entry.config().componentModel() == ComponentModel.LAZY;
    var factory = entry.config().creationStrategy() == CreationStrategy.FACTORY;
    if (factory) {
      // use the helper's own constructor (FACTORY = no instanceResolver)
      return lazy
              ? "    registrar.register(\"%s\", () -> new %s());\n".formatted(name, simpleName)
              : "    registrar.register(\"%s\", new %s());\n".formatted(name, simpleName);
    }
    // STANDARD: defer to the provided instanceResolver
    return lazy
            ? "    registrar.register(\"%s\", () -> instanceResolver.resolve(%s.class));\n"
                    .formatted(name, simpleName)
            : "    registrar.register(\"%s\", instanceResolver.resolve(%s.class));\n"
                    .formatted(name, simpleName);
  }

  private void writeGeneratedHelperInstanceResolver() {
    var resolverPackage = commonPackage(entries);
    var resolverFqn = resolverPackage.isEmpty()
            ? INSTANCE_RESOLVER_CLASS
            : resolverPackage + "." + INSTANCE_RESOLVER_CLASS;
    try {
      var sourceFile = processingEnv.getFiler().createSourceFile(resolverFqn);
      try (var writer = new PrintWriter(sourceFile.openWriter())) {
        var instantiable = entries.stream().filter(HelperEntry::noArgConstructor).toList();
        var imports = instantiable.stream()
                .filter(entry -> !packageOf(entry.fqn()).equals(resolverPackage))
                .map(entry -> "import " + entry.fqn() + ";\n")
                .collect(Collectors.joining());
        var mappings = instantiable.stream()
                .map(entry -> "    if (helperClass.equals(%s.class)) return new %s();\n"
                        .formatted(entry.fqn(), simpleNameOf(entry.fqn())))
                .collect(Collectors.joining());
        var packageLine = resolverPackage.isEmpty()
                ? ""
                : "package %s;\n\n".formatted(resolverPackage);
        var template = // language=java
                """
                        ${packageLine}import cbs.nova.dsl.Executable;
                        import cbs.nova.dsl.helper.HelperInstanceResolver;
                        import org.jspecify.annotations.NonNull;
                                                ${imports}

                        public final class ${resolverClass} implements HelperInstanceResolver {
                          @Override
                          public @NonNull Executable<?, ?> resolve(@NonNull Class<?> helperClass) {
                        ${mappings}    throw new IllegalStateException(
                              "Helper is not registered by this generated factory: " + helperClass.getName());
                          }
                        }
                        """;
        writer.print(Substitutor.format(template, Map.of(
                "packageLine", packageLine,
                "imports", imports,
                "resolverClass", INSTANCE_RESOLVER_CLASS,
                "mappings", mappings)));
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Failed to write GeneratedHelperInstanceResolver: " + e.getMessage());
    }
  }

  private void writeResolverServiceFile() {
    var resolverPackage = commonPackage(entries);
    var resolverFqn = resolverPackage.isEmpty()
            ? RESOLVER_CLASS
            : resolverPackage + "." + RESOLVER_CLASS;
    writeServiceFile(
            "cbs.nova.dsl.helper.HelperResolver",
            resolverFqn,
            "HelperResolver");
  }

  private void writeInstanceResolverServiceFile() {
    var resolverPackage = commonPackage(entries);
    var resolverFqn = resolverPackage.isEmpty()
            ? INSTANCE_RESOLVER_CLASS
            : resolverPackage + "." + INSTANCE_RESOLVER_CLASS;
    writeServiceFile(
            "cbs.nova.dsl.helper.HelperInstanceResolver",
            resolverFqn,
            "HelperInstanceResolver");
  }

  private void writeServiceFile(String serviceInterface, String resolverFqn, String label) {
    try {
      var resource = processingEnv.getFiler().createResource(
              StandardLocation.CLASS_OUTPUT, "",
              "META-INF/services/" + serviceInterface);
      try (var writer = new PrintWriter(resource.openWriter())) {
        writer.print(String.format("%s%n", resolverFqn));
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Failed to write " + label + " service file: " + e.getMessage());
    }
  }

  private String packageOf(String fqn) {
    var idx = fqn.lastIndexOf('.');
    return idx < 0 ? "" : fqn.substring(0, idx);
  }

  private String simpleNameOf(String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1);
  }

  private String commonPackage(List<HelperEntry> entries) {
    if (entries.isEmpty()) {
      return "";
    }
    var first = packageOf(entries.get(0).fqn()).split("\\.");
    var common = first.length;
    for (var i = 1; i < entries.size(); i++) {
      var segs = packageOf(entries.get(i).fqn()).split("\\.");
      var matched = 0;
      while (matched < common && matched < segs.length && segs[matched].equals(first[matched])) {
        matched++;
      }
      common = matched;
      if (common == 0) {
        return "";
      }
    }
    return String.join(".", Arrays.copyOf(first, common));
  }

  private record HelperConfig(String name, ComponentModel componentModel,
          CreationStrategy creationStrategy) {

  }

  /**
   * A helper without a public no-arg constructor (e.g. requires Spring-injected dependencies like
   * {@code HttpClient} or {@code ObjectMapper}, or is a {@code @SpringHelper} that should be wired
   * by Spring) cannot be direct-instantiated by generated code; it is excluded from
   * {@code GeneratedHelperInstanceResolver} and left to the Spring resolver.
   */
  private boolean hasNoArgConstructor(TypeElement type) {
    return type.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .anyMatch(e -> ((ExecutableElement) e).getParameters()
                    .isEmpty());
  }

  private record HelperEntry(String fqn, HelperConfig config, boolean noArgConstructor) {

  }

}
