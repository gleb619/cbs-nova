package cbs.nova.misc.codegen;

import cbs.nova.dsl.Helper;
import cbs.nova.dsl.utils.Substitutor;

import java.util.Map;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("cbs.nova.dsl.Helper")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class HelperSpiProcessor extends AbstractProcessor {

  private static final String RESOLVER_CLASS = "GeneratedHelperResolver";

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
                .map(entry -> {
                  var simpleName = entry.fqn().substring(entry.fqn().lastIndexOf('.') + 1);
                  return "    registrar.register(\"" + entry.name() + "\", new " + simpleName
                          + "());\n";
                })
                .collect(Collectors.joining());
        var packageLine = resolverPackage.isEmpty()
                ? ""
                : "package " + resolverPackage + ";\n\n";
        var template = """
                ${packageLine}import cbs.nova.dsl.Executable;
                import cbs.nova.dsl.HelperRegistrar;
                import cbs.nova.dsl.HelperResolver;
                ${imports}

                public final class ${resolverClass} implements HelperResolver {
                  @Override
                  public void registerHelpers(HelperRegistrar registrar) {
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

  private void writeServiceFile() {
    var resolverPackage = commonPackage(entries);
    var resolverFqn = resolverPackage.isEmpty()
            ? RESOLVER_CLASS
            : resolverPackage + "." + RESOLVER_CLASS;
    try {
      var resource = processingEnv.getFiler().createResource(
              StandardLocation.CLASS_OUTPUT, "",
              "META-INF/services/cbs.nova.dsl.HelperResolver");
      try (var writer = new PrintWriter(resource.openWriter())) {
        writer.print(String.format("%s%n", resolverFqn));
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Failed to write HelperResolver service file: " + e.getMessage());
    }
  }

  private static String packageOf(String fqn) {
    var idx = fqn.lastIndexOf('.');
    return idx < 0 ? "" : fqn.substring(0, idx);
  }

  private static String commonPackage(List<HelperEntry> entries) {
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

  private record HelperEntry(String fqn, String name) {
  }
}
