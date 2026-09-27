package cbs.nova.starter.service;

import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@FunctionalInterface
interface ObjectMatcher {

  boolean matches(String term, HelperCatalogEntry entry);

  static ObjectMatcher exact() {
    return (term, entry) -> text(entry).contains(term);
  }

  static ObjectMatcher all() {
    return (_, _) -> Boolean.TRUE;
  }

  static ObjectMatcher fuzzy() {
    return (term, entry) -> {
      if (term == null || term.isBlank()) {
        return true;
      }
      String[] queryTokens = tokens(term);
      String[] candidateTokens = tokens(text(entry));
      for (String query : queryTokens) {
        if (query.isEmpty()) {
          continue;
        }
        int maxThreshold = Math.max(1, Math.min(3, query.length() / 4));
        for (String candidate : candidateTokens) {
          if (candidate.isEmpty()) {
            continue;
          }
          int allowed = Math.max(1, Math.min(maxThreshold, candidate.length() / 4));
          if (levenshtein(query, candidate) <= allowed) {
            return true;
          }
        }
      }
      return false;
    };
  }

  static ObjectMatcher cosine() {
    return (term, entry) -> {
      if (term == null || term.isBlank()) {
        return true;
      }
      String[] queryTokens = tokens(term);
      String[] candidateTokens = tokens(text(entry));
      double similarity = cosineSimilarity(queryTokens, candidateTokens);
      return similarity >= 0.5D;
    };
  }

  private static String text(HelperCatalogEntry entry) {
    String description = entry.description() == null ? "" : entry.description();
    String inputType = entry.inputType() == null ? "" : entry.inputType();
    String outputType = entry.outputType() == null ? "" : entry.outputType();
    return (entry.name() + " " + description + " " + inputType + " " + outputType)
            .toLowerCase(Locale.ROOT);
  }

  private static String[] tokens(String value) {
    String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
    return spaced.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
  }

  private static int levenshtein(String a, String b) {
    int n = a.length();
    int m = b.length();
    if (n == 0) {
      return m;
    }
    if (m == 0) {
      return n;
    }
    int[] previous = new int[m + 1];
    int[] current = new int[m + 1];
    for (int j = 0; j <= m; j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= n; i++) {
      current[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= m; j++) {
        int cost = ca == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[m];
  }

  private static double cosineSimilarity(String[] queryTokens, String[] candidateTokens) {
    Map<String, Integer> queryFreq = termFrequency(queryTokens);
    Map<String, Integer> candidateFreq = termFrequency(candidateTokens);
    if (queryFreq.isEmpty() || candidateFreq.isEmpty()) {
      return 0.0D;
    }
    double dot = 0.0D;
    for (Map.Entry<String, Integer> e : queryFreq.entrySet()) {
      Integer candidateCount = candidateFreq.get(e.getKey());
      if (candidateCount != null) {
        dot += e.getValue() * candidateCount;
      }
    }
    double queryNorm = euclideanNorm(queryFreq);
    double candidateNorm = euclideanNorm(candidateFreq);
    if (queryNorm == 0.0D || candidateNorm == 0.0D) {
      return 0.0D;
    }
    return dot / (queryNorm * candidateNorm);
  }

  private static Map<String, Integer> termFrequency(String[] tokens) {
    Map<String, Integer> freq = new HashMap<>();
    for (String token : tokens) {
      if (token.isEmpty()) {
        continue;
      }
      freq.merge(token, 1, Integer::sum);
    }
    return freq;
  }

  private static double euclideanNorm(Map<String, Integer> frequencies) {
    double sum = 0.0D;
    for (int count : frequencies.values()) {
      sum += (double) count * count;
    }
    return Math.sqrt(sum);
  }
}
