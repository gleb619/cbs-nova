package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.CaseConvertIn;
import cbs.nova.starter.helper.model.CaseConvertOut;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Converts a string between {@code camel}, {@code kebab}, {@code snake}, and {@code title} case.
 *
 * <p>
 * The input is first split into words: any run of non-alphanumeric characters acts as a separator,
 * a lowercase letter or digit followed by an uppercase letter starts a new word ({@code fooBar} →
 * {@code foo}, {@code Bar}), and an acronym followed by a capitalized word also splits
 * ({@code parseXML} → {@code parse}, {@code XML}; {@code XMLHttpRequest} → {@code XML},
 * {@code Http}, {@code Request}). Digits stay attached to their word ({@code v2Api} → {@code v2},
 * {@code Api}).
 *
 * <p>
 * Modes (case-insensitive):
 * <ul>
 * <li>{@code "camel"}: first word lowercased, subsequent words capitalized ({@code fooBar}).</li>
 * <li>{@code "kebab"}: words lowercased and joined with {@code -} ({@code foo-bar}).</li>
 * <li>{@code "snake"}: words lowercased and joined with {@code _} ({@code foo_bar}).</li>
 * <li>{@code "title"}: every word capitalized and joined with spaces ({@code Foo Bar}).</li>
 * </ul>
 *
 * <p>
 * Input is required and must be non-empty. A null or unrecognized mode is rejected with an
 * {@link IllegalArgumentException}. Note: this deliberately does not reuse
 * {@code Strings.toKebabCase} — that util splits every uppercase letter (so acronyms become single
 * letters, e.g. {@code BATCH} → {@code b-a-t-c-h}) and never splits at digit boundaries, which does
 * not match the word semantics above.
 */
@Helper(name = "caseConvert")
public class CaseConvertHelper implements Executable<CaseConvertIn, CaseConvertOut> {

  @Override
  public @NonNull Result<CaseConvertOut> execute(@NonNull Context<CaseConvertIn> ctx) {
    try {
      CaseConvertIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("caseConvert.input is required"));
      }
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      List<String> words = splitWords(input.input());
      return switch (mode) {
        case "camel" -> Result.success(new CaseConvertOut(toCamel(words)));
        case "kebab" -> Result.success(new CaseConvertOut(join(words, "-")));
        case "snake" -> Result.success(new CaseConvertOut(join(words, "_")));
        case "title" -> Result.success(new CaseConvertOut(toTitle(words)));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "caseConvert.mode must be 'camel', 'kebab', 'snake', or 'title', was: "
                                + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static List<String> splitWords(String input) {
    List<String> words = new ArrayList<>();
    StringBuilder word = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (!Character.isLetterOrDigit(c)) {
        flushWord(words, word);
        continue;
      }
      if (word.length() > 0 && Character.isUpperCase(c)) {
        char prev = word.charAt(word.length() - 1);
        boolean startsNewWord = Character.isLowerCase(prev) || Character.isDigit(prev);
        // acronym end: upper, upper, lower boundary (XML|Http)
        if (!startsNewWord && i + 1 < input.length()
                && Character.isLowerCase(input.charAt(i + 1))) {
          startsNewWord = true;
        }
        if (startsNewWord) {
          flushWord(words, word);
        }
      }
      word.append(c);
    }
    flushWord(words, word);
    return words;
  }

  private static void flushWord(List<String> words, StringBuilder word) {
    if (word.length() > 0) {
      words.add(word.toString());
      word.setLength(0);
    }
  }

  private static String join(List<String> words, String separator) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      if (i > 0) {
        sb.append(separator);
      }
      sb.append(words.get(i).toLowerCase(Locale.ROOT));
    }
    return sb.toString();
  }

  private static String toCamel(List<String> words) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      if (i == 0) {
        sb.append(word.toLowerCase(Locale.ROOT));
      } else {
        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return sb.toString();
  }

  private static String toTitle(List<String> words) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      if (i > 0) {
        sb.append(' ');
      }
      String word = words.get(i);
      sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return sb.toString();
  }
}
