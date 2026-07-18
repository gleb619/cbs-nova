package cbs.nova.dsl.codegen.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class CodegenNaming {

  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  public String versionedPackage(@NonNull String name, @NonNull String version) {
    return versionedPackage(name, version, null);
  }

  public String versionedPackage(
          @NonNull String name,
          @NonNull String version,
          @Nullable String targetPackage) {
    String basePackage = (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage
            : BASE_PACKAGE;
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    String versionSegment = version.replaceAll("[^a-z0-9]", "");
    if (!versionSegment.isEmpty() && Character.isDigit(versionSegment.charAt(0))) {
      versionSegment = "v" + versionSegment;
    }
    return basePackage + "." + nameSegment + "." + versionSegment;
  }
}
