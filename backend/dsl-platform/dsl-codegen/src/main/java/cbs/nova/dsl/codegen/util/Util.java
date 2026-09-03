package cbs.nova.dsl.codegen.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Util {

  public static @NonNull String escapeJavaString(@NonNull String value) {
    var sb = new StringBuilder(value.length() + 2);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  public static @NonNull String importBlock(@NonNull Class<?>... types) {
    var imports = Arrays.stream(types)
            .filter(Objects::nonNull)
            .filter(type -> !type.getPackageName().startsWith("java.lang"))
            .map(type -> "import %s;".formatted(type.getCanonicalName()))
            .distinct()
            .collect(Collectors.joining("\n"));
    return imports.isEmpty() ? "" : "\n" + imports + "\n";
  }

}
