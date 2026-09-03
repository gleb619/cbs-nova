package cbs.nova.dsl.codegen.preprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.jspecify.annotations.NonNull;

public final class ModelPreprocessor {

  private final String JSON_IMPORT = "io.avaje.jsonb.Json";
  private final String JSON_ANNOTATION_SIMPLE = "Json";
  private final String JSON_ANNOTATION_QUALIFIED = "io.avaje.jsonb.Json";



  public @NonNull Result preprocess(
          @NonNull String fileName,
          @NonNull String rawSource,
          @NonNull String targetPackage) {
    if (targetPackage.isBlank()) {
      throw new IllegalArgumentException("targetPackage is required for model preprocessing");
    }
    var parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));
    var parseResult = parser.parse(rawSource);
    var cu = parseResult.getResult()
            .orElseThrow(() -> new IllegalArgumentException(
                    fileName + " is not valid Java source: " + parseResult.getProblems()));

    cu.setPackageDeclaration(new PackageDeclaration(new Name(targetPackage)));

    boolean hasJsonAnnotation = addMissingJsonAnnotations(cu);
    if (hasJsonAnnotation && cu.getImports().stream().noneMatch(
            i -> i.getNameAsString().equals(JSON_IMPORT))) {
      cu.addImport(JSON_IMPORT);
    }

    var output = cu.toString();
    return new Result(className(fileName), output);
  }

  private boolean addMissingJsonAnnotations(CompilationUnit cu) {
    boolean modified = false;
    for (var type : cu.getTypes()) {
      modified |= annotateType(type);
    }
    return modified;
  }

  private boolean annotateType(TypeDeclaration<?> type) {
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

  private boolean shouldAnnotate(TypeDeclaration<?> type) {
    if (type instanceof ClassOrInterfaceDeclaration ci && ci.isInterface()) {
      return false;
    }
    return !(type instanceof EnumDeclaration);
  }

  private boolean hasJsonAnnotation(TypeDeclaration<?> type) {
    return type.getAnnotations().stream().anyMatch(a -> {
      var name = a.getNameAsString();
      return name.equals(JSON_ANNOTATION_SIMPLE) || name.equals(JSON_ANNOTATION_QUALIFIED);
    });
  }

  private @NonNull String className(@NonNull String fileName) {
    if (!fileName.endsWith(".java")) {
      throw new IllegalArgumentException("Model source file must end with .java: " + fileName);
    }
    return fileName.substring(0, fileName.length() - ".java".length());
  }

  public record Result(@NonNull String className, @NonNull String preprocessedSource) {
  }
}
