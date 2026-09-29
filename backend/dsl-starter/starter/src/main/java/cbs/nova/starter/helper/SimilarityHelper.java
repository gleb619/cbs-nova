package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.SimilarityIn;
import cbs.nova.starter.helper.model.SimilarityOut;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Computes a normalized string similarity score in {@code [0.0, 1.0]}.
 *
 * <p>
 * Modes (case-insensitive):
 * <ul>
 * <li>{@code "levenshtein"}: Levenshtein edit distance normalized by the maximum input length
 * ({@code 1.0 - distance / max(|a|, |b|)}). Unicode-safe via {@code char}-by-{@code char} DP.</li>
 * <li>{@code "jaroWinkler"}: Jaro similarity with Winkler prefix boost ({@code p = 0.1}, prefix
 * length up to {@code 4}).</li>
 * <li>{@code "ratio"}: Dice bigram coefficient ({@code 2 * |intersection| / (|a-bigrams| +
 * |b-bigrams|)}).</li>
 * </ul>
 *
 * <p>
 * {@code caseSensitive} defaults to {@code false}; when false, both inputs are lower-cased via
 * {@link Locale#ROOT} before scoring. Equal strings always score {@code 1.0}; two empty strings
 * score {@code 1.0}; a non-empty and an empty string score {@code 0.0}. A {@code null} mode,
 * {@code a}, or {@code b} yields an {@link IllegalArgumentException} via {@link Result#failure}
 * rather than throwing.
 */
@Helper(name = "similarity")
public class SimilarityHelper implements Executable<SimilarityIn, SimilarityOut> {

  private static final double WINKLER_SCALING = 0.1;
  private static final int WINKLER_PREFIX_CAP = 4;

  @Override
  public @NonNull Result<SimilarityOut> execute(@NonNull Context<SimilarityIn> ctx) {
    try {
      SimilarityIn input = ctx.body();
      if (input.a() == null || input.b() == null) {
        return Result.failure(
                new IllegalArgumentException("similarity.a and similarity.b are required"));
      }
      boolean caseSensitive = input.caseSensitive() != null && input.caseSensitive();
      String a = caseSensitive ? input.a() : input.a().toLowerCase(Locale.ROOT);
      String b = caseSensitive ? input.b() : input.b().toLowerCase(Locale.ROOT);

      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      double score = switch (mode) {
        case "levenshtein" -> levenshtein(a, b);
        case "jarowinkler" -> jaroWinkler(a, b);
        case "ratio" -> diceBigram(a, b);
        case null, default -> throw new IllegalArgumentException(
                "similarity.mode must be 'levenshtein', 'jaroWinkler', or 'ratio', was: "
                        + input.mode());
      };
      return Result.success(new SimilarityOut(score));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static double levenshtein(String a, String b) {
    if (a.equals(b)) {
      return 1.0;
    }
    int lenA = a.length();
    int lenB = b.length();
    if (lenA == 0 || lenB == 0) {
      return 0.0;
    }
    int maxLen = Math.max(lenA, lenB);
    int[] prev = new int[lenB + 1];
    int[] curr = new int[lenB + 1];
    for (int j = 0; j <= lenB; j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= lenA; i++) {
      curr[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= lenB; j++) {
        int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(
                Math.min(curr[j - 1] + 1, prev[j] + 1),
                prev[j - 1] + cost);
      }
      int[] swap = prev;
      prev = curr;
      curr = swap;
    }
    int distance = prev[lenB];
    return 1.0 - ((double) distance / (double) maxLen);
  }

  private static double jaroWinkler(String a, String b) {
    if (a.equals(b)) {
      return 1.0;
    }
    int lenA = a.length();
    int lenB = b.length();
    if (lenA == 0 || lenB == 0) {
      return 0.0;
    }
    int window = Math.max(lenA, lenB) / 2 - 1;
    if (window < 0) {
      window = 0;
    }
    boolean[] aMatches = new boolean[lenA];
    boolean[] bMatches = new boolean[lenB];
    int matches = 0;
    for (int i = 0; i < lenA; i++) {
      int start = Math.max(0, i - window);
      int end = Math.min(i + window + 1, lenB);
      char ca = a.charAt(i);
      for (int j = start; j < end; j++) {
        if (!bMatches[j] && ca == b.charAt(j)) {
          aMatches[i] = true;
          bMatches[j] = true;
          matches++;
          break;
        }
      }
    }
    if (matches == 0) {
      return 0.0;
    }
    int transpositions = 0;
    int k = 0;
    for (int i = 0; i < lenA; i++) {
      if (aMatches[i]) {
        while (!bMatches[k]) {
          k++;
        }
        if (a.charAt(i) != b.charAt(k)) {
          transpositions++;
        }
        k++;
      }
    }
    double m = (double) matches;
    double jaro = (m / lenA + m / lenB + (m - transpositions / 2.0) / m) / 3.0;

    int prefix = 0;
    int cap = Math.min(WINKLER_PREFIX_CAP, Math.min(lenA, lenB));
    while (prefix < cap && a.charAt(prefix) == b.charAt(prefix)) {
      prefix++;
    }
    return jaro + prefix * WINKLER_SCALING * (1.0 - jaro);
  }

  private static double diceBigram(String a, String b) {
    if (a.equals(b)) {
      return 1.0;
    }
    if (a.length() < 2 || b.length() < 2) {
      return 0.0;
    }
    java.util.HashMap<String, Integer> countsA = new java.util.HashMap<>();
    for (int i = 0; i < a.length() - 1; i++) {
      String bigram = a.substring(i, i + 2);
      countsA.merge(bigram, 1, Integer::sum);
    }
    int intersection = 0;
    int totalB = 0;
    java.util.HashMap<String, Integer> countsB = new java.util.HashMap<>();
    for (int i = 0; i < b.length() - 1; i++) {
      String bigram = b.substring(i, i + 2);
      countsB.merge(bigram, 1, Integer::sum);
      totalB++;
    }
    for (var entry : countsB.entrySet()) {
      Integer ca = countsA.get(entry.getKey());
      if (ca != null) {
        intersection += Math.min(ca, entry.getValue());
      }
    }
    int totalA = a.length() - 1;
    if (totalA + totalB == 0) {
      return 1.0;
    }
    return (2.0 * intersection) / (totalA + totalB);
  }
}
