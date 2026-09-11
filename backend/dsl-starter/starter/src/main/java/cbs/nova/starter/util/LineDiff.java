package cbs.nova.starter.util;

import static cbs.nova.starter.core.StarterConstants.LINE_DIFF_CONTEXT_LINES;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.VcsModels.DiffHunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal LCS-based line diff producing unified-style hunks with {@code +}/{@code -}/{@code " "}
 * prefixed lines. Textual only — no AST awareness. Hunk count is capped; when the cap is exceeded
 * the result is marked truncated.
 */
// TODO: remove, put to a `dsl-builder` or to `dsl-api` module
@Deprecated(forRemoval = true)
public final class LineDiff {

  /** Default maximum number of hunks returned before truncation kicks in. */

  private LineDiff() {
  }

  /**
   * Result of a line diff: the hunks that fit under the cap and whether truncation occurred.
   */
  public record Result(List<DiffHunk> hunks, boolean truncated) {

  }

  public static Result diff(String before, String after) {
    return diff(before, after, StarterConstants.DEFAULT_MAX_HUNKS);
  }

  public static Result diff(String before, String after, int maxHunks) {
    String[] beforeLines = splitLines(before);
    String[] afterLines = splitLines(after);

    List<Op> script = editScript(beforeLines, afterLines);
    List<DiffHunk> hunks = toHunks(script, beforeLines, afterLines, maxHunks);
    boolean truncated = countChangeBlocks(script) > hunks.size();
    return new Result(hunks, truncated);
  }

  private static String[] splitLines(String value) {
    if (value == null || value.isEmpty()) {
      return new String[0];
    }
    String normalized = value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
    return normalized.split("\n", -1);
  }

  private sealed interface Op {

    record Same(int beforeIndex, int afterIndex) implements Op {

    }

    record Del(int beforeIndex) implements Op {

    }

    record Add(int afterIndex) implements Op {

    }
  }

  private static List<Op> editScript(String[] before, String[] after) {
    int m = before.length;
    int n = after.length;
    // dp[i][j] = LCS length of before[i:] and after[j:]
    int[][] dp = new int[m + 1][n + 1];
    for (int i = m - 1; i >= 0; i--) {
      for (int j = n - 1; j >= 0; j--) {
        dp[i][j] = before[i].equals(after[j])
                ? dp[i + 1][j + 1] + 1
                : Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }

    List<Op> script = new ArrayList<>();
    int i = 0;
    int j = 0;
    while (i < m && j < n) {
      if (before[i].equals(after[j])) {
        script.add(new Op.Same(i, j));
        i++;
        j++;
      } else if (dp[i + 1][j] >= dp[i][j + 1]) {
        script.add(new Op.Del(i));
        i++;
      } else {
        script.add(new Op.Add(j));
        j++;
      }
    }
    while (i < m) {
      script.add(new Op.Del(i));
      i++;
    }
    while (j < n) {
      script.add(new Op.Add(j));
      j++;
    }
    return script;
  }

  private static int countChangeBlocks(List<Op> script) {
    int blocks = 0;
    boolean inBlock = false;
    for (Op op : script) {
      if (op instanceof Op.Same) {
        inBlock = false;
      } else if (!inBlock) {
        blocks++;
        inBlock = true;
      }
    }
    return blocks;
  }

  private static List<DiffHunk> toHunks(List<Op> script, String[] before, String[] after,
          int maxHunks) {
    List<DiffHunk> hunks = new ArrayList<>();
    int i = 0;
    int size = script.size();
    while (i < size && hunks.size() < maxHunks) {
      if (script.get(i) instanceof Op.Same) {
        i++;
        continue;
      }
      // A change block starts at i; extend it, merging nearby change blocks.
      int blockEnd = i;
      while (blockEnd < size && !(script.get(blockEnd) instanceof Op.Same)) {
        blockEnd++;
      }
      // Greedily merge a following change block when the gap between them is small
      // enough that their context windows would overlap.
      while (blockEnd < size) {
        int gapEnd = blockEnd;
        while (gapEnd < size && gapEnd - blockEnd < 2 * LINE_DIFF_CONTEXT_LINES
                && script.get(gapEnd) instanceof Op.Same) {
          gapEnd++;
        }
        boolean moreChanges = gapEnd < size && !(script.get(gapEnd) instanceof Op.Same);
        if (moreChanges && gapEnd - blockEnd <= 2 * LINE_DIFF_CONTEXT_LINES) {
          blockEnd = gapEnd;
          while (blockEnd < size && !(script.get(blockEnd) instanceof Op.Same)) {
            blockEnd++;
          }
        } else {
          break;
        }
      }

      int contextBefore = Math.min(LINE_DIFF_CONTEXT_LINES, i);
      int contextAfter = Math.min(LINE_DIFF_CONTEXT_LINES, size - blockEnd);
      int start = i - contextBefore;
      int end = blockEnd + contextAfter;

      List<String> lines = new ArrayList<>();
      int beforeStart = -1;
      int beforeCount = 0;
      int afterStart = -1;
      int afterCount = 0;
      for (int k = start; k < end; k++) {
        Op op = script.get(k);
        switch (op) {
          case Op.Same same -> {
            if (beforeStart < 0) {
              beforeStart = same.beforeIndex();
            }
            if (afterStart < 0) {
              afterStart = same.afterIndex();
            }
            beforeCount++;
            afterCount++;
            lines.add(" " + before[same.beforeIndex()]);
          }
          case Op.Del del -> {
            if (beforeStart < 0) {
              beforeStart = del.beforeIndex();
            }
            beforeCount++;
            lines.add("-" + before[del.beforeIndex()]);
          }
          case Op.Add add -> {
            if (afterStart < 0) {
              afterStart = add.afterIndex();
            }
            afterCount++;
            lines.add("+" + after[add.afterIndex()]);
          }
          default -> throw new IllegalStateException("unknown op: " + op);
        }
      }
      if (beforeStart < 0) {
        beforeStart = 0;
      }
      if (afterStart < 0) {
        afterStart = 0;
      }
      hunks.add(new DiffHunk(beforeStart, beforeCount, afterStart, afterCount, lines));
      i = blockEnd;
    }
    return hunks;
  }

}
