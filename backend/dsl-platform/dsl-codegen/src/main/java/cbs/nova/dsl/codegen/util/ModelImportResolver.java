package cbs.nova.dsl.codegen.util;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class ModelImportResolver {

  private static final Pattern IMPORT_LINE = Pattern.compile(
          "(?m)^(\\s*import\\s+)([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$*][\\w$*]*)*)(\\s*;[^\\n]*)$");

  private final CodegenNaming codegenNaming;

  public @NonNull List<ModelImport> extract(
          @NonNull String source,
          @Nullable String basePackage,
          @Nullable String version,
          @NonNull Set<String> modelClassNames) {
    var imports = new ArrayList<ModelImport>();
    var matcher = IMPORT_LINE.matcher(source);
    while (matcher.find()) {
      classify(matcher.group(2), basePackage, version, modelClassNames, matcher.group(0))
              .ifPresent(imports::add);
    }
    return imports;
  }

  public @NonNull String rewrite(
          @NonNull String source,
          @Nullable String basePackage,
          @Nullable String version,
          @NonNull Map<String, String> modelPackages,
          @NonNull Set<String> modelClassNames) {
    var matcher = IMPORT_LINE.matcher(source);
    var sb = new StringBuilder();
    while (matcher.find()) {
      var replacement = classify(matcher.group(2), basePackage, version, modelClassNames,
              matcher.group(0))
              .flatMap(imp -> {
                var newPackage = modelPackages.get(imp.modelClass());
                if (newPackage == null || newPackage.isBlank() || imp.member().isBlank()) {
                  return Optional.<String>empty();
                }
                return Optional.of(
                        matcher.group(1) + newPackage + "." + imp.modelClass() + "." + imp.member()
                                + matcher.group(3));
              });
      if (replacement.isPresent()) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.get()));
      } else {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private @NonNull Optional<ModelImport> classify(
          @NonNull String importName,
          @Nullable String basePackage,
          @Nullable String version,
          @NonNull Set<String> modelClassNames,
          @NonNull String rawLine) {
    if (modelClassNames.isEmpty()) {
      return Optional.empty();
    }
    var parts = importName.split("\\.");
    var versionSegment = (version != null && !version.isBlank())
            ? codegenNaming.versionSegment(version)
            : null;
    if (basePackage != null && !basePackage.isBlank()) {
      var baseDepth = basePackage.split("\\.").length;
      if (parts.length >= baseDepth + 2 && modelClassNames.contains(parts[baseDepth])) {
        return Optional.of(new ModelImport(importName, parts[baseDepth],
                join(parts, baseDepth + 1), ModelImport.Style.BASE));
      }
      if (versionSegment != null && parts.length >= baseDepth + 3
              && parts[baseDepth].equals(versionSegment)
              && modelClassNames.contains(parts[baseDepth + 1])) {
        return Optional.of(new ModelImport(importName, parts[baseDepth + 1],
                join(parts, baseDepth + 2), ModelImport.Style.BASE_VERSION));
      }
    }
    if (versionSegment != null && parts.length >= 3 && parts[0].equals(versionSegment)
            && modelClassNames.contains(parts[1])) {
      return Optional.of(new ModelImport(importName, parts[1],
              join(parts, 2), ModelImport.Style.VERSION));
    }
    if (parts.length >= 2 && modelClassNames.contains(parts[0])) {
      if (basePackage == null || basePackage.isBlank()) {
        if (modelClassNames.size() > 1) {
          throw new IllegalArgumentException(
                  "Ambiguous model import '%s': multiple model classes exist, use a qualified import"
                          .formatted(rawLine.trim()));
        }
      }
      return Optional.of(new ModelImport(importName, parts[0],
              join(parts, 1), ModelImport.Style.BARE));
    }
    return Optional.empty();
  }

  private static @NonNull String join(@NonNull String[] parts, int from) {
    return String.join(".", Arrays.asList(parts).subList(from, parts.length));
  }

  public record ModelImport(
          @NonNull String rawImport,
          @NonNull String modelClass,
          @NonNull String member,
          @NonNull Style style) {

    public enum Style {
      BASE, BASE_VERSION, VERSION, BARE
    }
  }
}
