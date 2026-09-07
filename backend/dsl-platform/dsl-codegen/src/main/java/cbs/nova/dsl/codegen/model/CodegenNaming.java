package cbs.nova.dsl.codegen.model;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class CodegenNaming {

  private final String defaultBasePackage;

  public @NonNull String registryPackage(@Nullable String targetPackage) {
    return (targetPackage != null && !targetPackage.isBlank()) ? targetPackage : defaultBasePackage;
  }

  public String versionedPackage(
          @NonNull String name,
          @NonNull String version,
          @Nullable String targetPackage) {
    String basePackage = (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage
            : defaultBasePackage;
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    return basePackage + "." + nameSegment + "." + versionSegment(version);
  }

  public String versionedBasePackage(
          @NonNull String version,
          @Nullable String targetPackage) {
    String basePackage = (targetPackage != null && !targetPackage.isBlank())
            ? targetPackage
            : defaultBasePackage;
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
