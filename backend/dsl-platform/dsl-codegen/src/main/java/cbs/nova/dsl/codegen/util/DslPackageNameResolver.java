package cbs.nova.dsl.codegen.util;

import static cbs.nova.dsl.codegen.CompilerConstants.DEFAULT_BUILD_VERSION;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class DslPackageNameResolver {

  private final CodegenNaming codegenNaming;

  public @NonNull String resolve(
          @Nullable String basePackage,
          @Nullable String version,
          @NonNull String dslFile,
          boolean useFileNameSubPackage) {
    var effectiveBasePackage = (basePackage != null && !basePackage.isBlank()) ? basePackage : null;
    var effectiveVersion = (version != null && !version.isBlank()) ? version : DEFAULT_BUILD_VERSION;
    if (!useFileNameSubPackage) {
      return codegenNaming.versionedBasePackage(effectiveVersion, effectiveBasePackage);
    }
    var className = dslFile.endsWith(".java")
            ? dslFile.substring(0, dslFile.length() - ".java".length())
            : dslFile;
    var name = className.endsWith("Dsl")
            ? className.substring(0, className.length() - "Dsl".length())
            : className;
    return codegenNaming.versionedPackage(name, effectiveVersion, effectiveBasePackage);
  }
}
