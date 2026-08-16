package cbs.nova.dsl.compact;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.jspecify.annotations.NonNull;

public final class ModelSourcePreprocessor {

  private static final String JSON_IMPORT = "io.avaje.jsonb.Json";
  private static final String JSON_ANNOTATION_SIMPLE = "Json";
  private static final String JSON_ANNOTATION_QUALIFIED = "io.avaje.jsonb.Json";

  public record Result(@NonNull String className, @NonNull String preprocessedSource) {
  }

  public static @NonNull Result preprocess(
          @NonNull String fileName,
          @NonNull String rawSource,
          @NonNull String targetPackage) {
    if (targetPackage.isBlank()) {
      throw new IllegalArgumentException("targetPackage is required for model preprocessing");
    }
    var parser = new JavaParser();
    var cu = parser.parse(rawSource).getResult()
            .orElseThrow(() -> new IllegalArgumentException(
                    fileName + " is not valid Java source"));
    LexicalPreservingPrinter.setup(cu);

    if (cu.getPackageDeclaration().isEmpty()) {
      cu.setPackageDeclaration(new PackageDeclaration(new Name(targetPackage)));
    }

    boolean hasJsonAnnotation = addMissingJsonAnnotations(cu);
    if (hasJsonAnnotation && cu.getImports().stream().noneMatch(
            i -> i.getNameAsString().equals(JSON_IMPORT))) {
      cu.addImport(JSON_IMPORT);
    }

    var output = LexicalPreservingPrinter.print(cu);
    return new Result(className(fileName), output);
  }

  private static boolean addMissingJsonAnnotations(CompilationUnit cu) {
    boolean modified = false;
    for (var type : cu.getTypes()) {
      modified |= annotateType(type);
    }
    return modified;
  }

  private static boolean annotateType(TypeDeclaration<?> type) {
    boolean modified = false;
    if (shouldAnnotate(type) && !hasJsonAnnotation(type)) {
      type.addAnnotation(JSON_ANNOTATION_SIMPLE);
      modified = true;
    }
    for (var member : type.getMembers()) {
      if (member instanceof TypeDeclaration<?> nested) {
        modified |= annotateType(nested);
      }
    }
    return modified;
  }

  private static boolean shouldAnnotate(TypeDeclaration<?> type) {
    if (type instanceof ClassOrInterfaceDeclaration ci && ci.isInterface()) {
      return false;
    }
    return !(type instanceof EnumDeclaration);
  }

  private static boolean hasJsonAnnotation(TypeDeclaration<?> type) {
    return type.getAnnotations().stream().anyMatch(a -> {
      var name = a.getNameAsString();
      return name.equals(JSON_ANNOTATION_SIMPLE) || name.equals(JSON_ANNOTATION_QUALIFIED);
    });
  }

  private static @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("Model source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }
}
