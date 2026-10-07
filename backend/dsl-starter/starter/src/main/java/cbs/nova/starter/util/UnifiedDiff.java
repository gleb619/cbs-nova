package cbs.nova.starter.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal unified line diff (T875) — no external dependency. Classic LCS dynamic program over the
 * two line arrays; emits GNU-style hunks with 3 lines of context. When the input exceeds
 * {@link #MAX_DIFF_CELLS} DP cells, falls back to a single whole-file-replace hunk so pathological
 * inputs cannot exhaust memory.
 */
public final class UnifiedDiff {

  private static final int CONTEXT = 3;
  private static final long MAX_DIFF_CELLS = 25_000_000L;

  private UnifiedDiff() {
  }

  /** Unified diff of {@code previous} → {@code current}; empty string when identical. */
  public static String diff(String previous, String current) {
    if (previous.equals(current)) {
      return "";
    }
    String[] oldLines = lines(previous);
    String[] newLines = lines(current);
    List<Kind> ops = (long) oldLines.length * newLines.length > MAX_DIFF_CELLS
            ? wholeFileReplace(oldLines.length, newLines.length)
            : lcsOps(oldLines, newLines);
    return render(oldLines, newLines, ops);
  }

  private static String[] lines(String text) {
    if (text.isEmpty()) {
      return new String[0];
    }
    String[] split = text.split("\n", -1);
    if (split.length > 0 && split[split.length - 1].isEmpty() && text.endsWith("\n")) {
      String[] trimmed = new String[split.length - 1];
      System.arraycopy(split, 0, trimmed, 0, trimmed.length);
      return trimmed;
    }
    return split;
  }

  private static List<Kind> wholeFileReplace(int oldCount, int newCount) {
    List<Kind> ops = new ArrayList<>(oldCount + newCount);
    for (int i = 0; i < oldCount; i++) {
      ops.add(Kind.DEL);
    }
    for (int i = 0; i < newCount; i++) {
      ops.add(Kind.ADD);
    }
    return ops;
  }

  /** LCS dynamic program; backtrack yields the per-line op sequence (KEEP/DEL/ADD). */
  private static List<Kind> lcsOps(String[] oldLines, String[] newLines) {
    int m = oldLines.length;
    int n = newLines.length;
    int[][] dp = new int[m + 1][n + 1];
    for (int i = m - 1; i >= 0; i--) {
      for (int j = n - 1; j >= 0; j--) {
        dp[i][j] = oldLines[i].equals(newLines[j])
                ? dp[i + 1][j + 1] + 1
                : Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
    List<Kind> ops = new ArrayList<>(m + n);
    int i = 0;
    int j = 0;
    while (i < m && j < n) {
      if (oldLines[i].equals(newLines[j])) {
        ops.add(Kind.KEEP);
        i++;
        j++;
      } else if (dp[i + 1][j] >= dp[i][j + 1]) {
        ops.add(Kind.DEL);
        i++;
      } else {
        ops.add(Kind.ADD);
        j++;
      }
    }
    while (i < m) {
      ops.add(Kind.DEL);
      i++;
    }
    while (j < n) {
      ops.add(Kind.ADD);
      j++;
    }
    return ops;
  }

  private static String render(String[] oldLines, String[] newLines, List<Kind> ops) {
    StringBuilder out = new StringBuilder();
    out.append("--- a/previous\n+++ b/current\n");
    int oldPos = 0;
    int newPos = 0;
    int i = 0;
    int n = ops.size();
    while (i < n) {
      if (ops.get(i) == Kind.KEEP) {
        oldPos++;
        newPos++;
        i++;
        continue;
      }
      // Change found: walk back up to CONTEXT keeps for leading context.
      int back = 0;
      while (i - back - 1 >= 0 && ops.get(i - back - 1) == Kind.KEEP && back < CONTEXT) {
        back++;
      }
      int hunkStart = i - back;
      // Extend forward until a gap of more than 2*CONTEXT unchanged lines ends the hunk.
      int j = i;
      int gap = 0;
      while (j < n) {
        if (ops.get(j) == Kind.KEEP) {
          gap++;
          if (gap > 2 * CONTEXT) {
            break;
          }
        } else {
          gap = 0;
        }
        j++;
      }
      // Trim trailing context to CONTEXT keeps.
      int trailing = gap;
      int hunkEnd = j - Math.max(0, trailing - CONTEXT);
      // Counts consumed before the hunk and inside it.
      int beforeOld = oldPos - back;
      int beforeNew = newPos - back;
      int hunkOld = 0;
      int hunkNew = 0;
      for (int k = hunkStart; k < hunkEnd; k++) {
        Kind kind = ops.get(k);
        if (kind != Kind.ADD) {
          hunkOld++;
        }
        if (kind != Kind.DEL) {
          hunkNew++;
        }
      }
      out.append("@@ -").append(hunkOld == 0 ? beforeOld : beforeOld + 1)
              .append(',').append(hunkOld)
              .append(" +").append(hunkNew == 0 ? beforeNew : beforeNew + 1)
              .append(',').append(hunkNew).append(" @@\n");
      int oldIdx = beforeOld;
      int newIdx = beforeNew;
      for (int k = hunkStart; k < hunkEnd; k++) {
        switch (ops.get(k)) {
          case KEEP -> {
            out.append(' ').append(oldLines[oldIdx++]).append('\n');
            newIdx++;
          }
          case DEL -> out.append('-').append(oldLines[oldIdx++]).append('\n');
          case ADD -> out.append('+').append(newLines[newIdx++]).append('\n');
        }
      }
      oldPos = beforeOld + hunkOld;
      newPos = beforeNew + hunkNew;
      i = hunkEnd;
    }
    return out.toString();
  }

  private enum Kind {
    KEEP, DEL, ADD
  }
}
