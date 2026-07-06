package cbs.nova.misc.codegen;

import cbs.nova.dsl.Helper;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor that discovers {@link Helper}-annotated classes and emits a
 * {@link cbs.nova.dsl.HelperResolver} SPI registration plus a {@code META-INF/services} descriptor
 * so the hosting runtime can load them via {@link java.util.ServiceLoader}.
 */
@SupportedAnnotationTypes("cbs.nova.dsl.Helper")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class HelperSpiProcessor extends AbstractProcessor {

  private static final String RESOLVER_PACKAGE = "cbs.nova.misc.codegen.spi";
  private static final String RESOLVER_CLASS = "GeneratedHelperResolver";
  private static final String RESOLVER_FQN = RESOLVER_PACKAGE + "." + RESOLVER_CLASS;

  private final List<HelperEntry> entries = new ArrayList<>();
  private boolean wrote = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      if (!wrote && !entries.isEmpty()) {
        writeSimpleResolver();
        writeServiceFile();
        wrote = true;
      }
      return false;
    }

    for (var annotation : annotations) {
      for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.CLASS) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                  "@Helper on non-class element ignored: " + element, element);
          continue;
        }
        var typeElement = (TypeElement) element;
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                  "@Helper on abstract class ignored: " + typeElement.getQualifiedName(), element);
          continue;
        }
        var helper = typeElement.getAnnotation(Helper.class);
        if (helper == null)
          continue;
        var fqn = typeElement.getQualifiedName().toString();
        if (!fqn.contains(".")) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                  "@Helper on default-package class ignored (cannot reference from generated SPI): "
                          + fqn,
                  element);
          continue;
        }
        entries.add(new HelperEntry(fqn, helper.name()));
      }
    }
    return false;
  }

  private void writeSimpleResolver() {
    try {
      var sourceFile = processingEnv.getFiler().createSourceFile(RESOLVER_FQN);
      try (var writer = new PrintWriter(sourceFile.openWriter())) {
        var imports = new StringBuilder();
        for (var entry : entries) {
          imports.append("import ").append(entry.fqn()).append(";\n");
        }
        var registrations = new StringBuilder();
        for (var entry : entries) {
          var simpleName = entry.fqn().contains(".")
                  ? entry.fqn().substring(entry.fqn().lastIndexOf('.') + 1)
                  : entry.fqn();
          registrations.append(String.format(
                  "    registrar.register(\"%s\", new %s());%n", entry.name(), simpleName));
        }
        var template = """
                package %s;

                import cbs.nova.dsl.Executable;
                import cbs.nova.dsl.HelperRegistrar;
                import cbs.nova.dsl.HelperResolver;
                %s
                public final class %s implements HelperResolver {
                  @Override
                  public void registerHelpers(HelperRegistrar registrar) {
                %s  }
                }
                """;
        writer.print(String.format(template, RESOLVER_PACKAGE, imports, RESOLVER_CLASS,
                registrations));
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Failed to write GeneratedHelperResolver: " + e.getMessage());
    }
  }

  private void writeServiceFile() {
    try {
      var resource = processingEnv.getFiler().createResource(
              StandardLocation.CLASS_OUTPUT, "",
              "META-INF/services/cbs.nova.dsl.HelperResolver");
      try (var writer = new PrintWriter(resource.openWriter())) {
        writer.print(String.format("%s%n", RESOLVER_FQN));
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Failed to write HelperResolver service file: " + e.getMessage());
    }
  }

  private record HelperEntry(String fqn, String name) {
  }
}
