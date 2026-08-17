package cbs.nova.dsl.codegen.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class ModelTypeExtractor {

  private final JavaParser parser = new JavaParser();

  public @NonNull List<String> extract(@NonNull String fileName, @NonNull String rawSource) {
    return extract(fileName, rawSource, null);
  }

  public @NonNull List<String> extract(
          @NonNull String fileName,
          @NonNull String rawSource,
          String packageName) {
    var result = parser.parse(rawSource);
    if (result.getResult().isEmpty()) {
      throw new IllegalArgumentException(fileName + " is not valid Java source");
    }
    var cu = result.getResult().get();
    if (packageName != null && !packageName.isBlank()) {
      cu.setPackageDeclaration(new PackageDeclaration(new Name(packageName)));
    }
    var names = new ArrayList<String>();
    for (var type : cu.getTypes()) {
      collectModelTypes(type, names);
    }
    return names;
  }

  private void collectModelTypes(TypeDeclaration<?> type, List<String> names) {
    if (!isModelType(type)) {
      return;
    }
    type.getFullyQualifiedName().ifPresent(names::add);

    for (var member : type.getMembers()) {
      if (member instanceof TypeDeclaration<?> nested) {
        collectModelTypes(nested, names);
      }
    }
  }

  private boolean isModelType(TypeDeclaration<?> type) {
    if (type instanceof ClassOrInterfaceDeclaration ci && ci.isInterface()) {
      return false;
    }
    return !(type instanceof EnumDeclaration);
  }
}
