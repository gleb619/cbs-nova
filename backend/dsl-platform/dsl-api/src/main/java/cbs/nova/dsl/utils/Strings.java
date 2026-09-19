package cbs.nova.dsl.utils;

import org.jspecify.annotations.NonNull;

/**
 * Small string helpers shared across the DSL modules.
 */
public final class Strings {

  private Strings() {
  }

  /**
   * Converts a camelCase/PascalCase name to kebab-case by inserting {@code -} before every
   * uppercase letter that follows a letter and lowercasing all characters. Non-letter boundaries
   * (digits, punctuation) never produce a separator.
   */
  public static @NonNull String toKebabCase(@NonNull String name) {
    var kebab = new StringBuilder(name.length() + 4);
    for (int i = 0; i < name.length(); i++) {
      var c = name.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && Character.isLetter(name.charAt(i - 1))) {
        kebab.append('-');
      }
      kebab.append(Character.toLowerCase(c));
    }
    return kebab.toString();
  }
}
