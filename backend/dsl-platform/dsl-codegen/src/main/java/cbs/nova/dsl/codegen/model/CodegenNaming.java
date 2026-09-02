package cbs.nova.dsl.codegen.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class CodegenNaming {

  //TODO: move to compiler/gradle settings
  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  public @NonNull String registryPackage(@Nullable String targetPackage) {
    return (targetPackage != null && !targetPackage.isBlank()) ? targetPackage : BASE_PACKAGE;
  }

  public String versionedPackage(
          @NonNull String name,
          @NonNull String version,
          @Nullable String targetPackage) {
    String basePackage = (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage
            : BASE_PACKAGE;
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    return basePackage + "." + nameSegment + "." + versionSegment(version);
  }

  public String versionedBasePackage(
          @NonNull String version,
          @Nullable String targetPackage) {
    String basePackage = (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage
            : BASE_PACKAGE;
    return basePackage + "." + versionSegment(version);
  }

  private @NonNull String versionSegment(@NonNull String version) {
    String versionSegment = version.replaceAll("[^a-z0-9]", "");
    if (!versionSegment.isEmpty() && Character.isDigit(versionSegment.charAt(0))) {
      versionSegment = "v" + versionSegment;
    }
    return versionSegment;
  }
}
