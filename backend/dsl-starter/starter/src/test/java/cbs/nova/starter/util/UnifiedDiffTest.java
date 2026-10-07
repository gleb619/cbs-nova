package cbs.nova.starter.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnifiedDiffTest {

  @Test
  void identicalTextsYieldEmptyDiff() {
    assertThat(UnifiedDiff.diff("a\nb\nc", "a\nb\nc")).isEmpty();
    assertThat(UnifiedDiff.diff("", "")).isEmpty();
  }

  @Test
  void changedLineProducesUnifiedHunk() {
    String diff = UnifiedDiff.diff("alpha\nbeta\ngamma", "alpha\nBETA\ngamma");

    assertThat(diff).contains("--- a/previous").contains("+++ b/current");
    assertThat(diff).contains("@@");
    assertThat(diff).contains("-beta").contains("+BETA");
    // unchanged lines stay context lines (no prefix)
    assertThat(diff.lines()).anyMatch(line -> line.equals(" alpha"));
  }

  @Test
  void addedAndRemovedLinesAreMarked() {
    String diff = UnifiedDiff.diff("one\ntwo\nthree", "one\nthree\nfour");

    assertThat(diff).contains("-two").contains("+four");
    assertThat(diff).doesNotContain("+three");
  }

  @Test
  void diffOfEmptyPreviousIsPureAddition() {
    String diff = UnifiedDiff.diff("", "hello");

    assertThat(diff).contains("@@ -0,0 +1,1 @@");
    assertThat(diff).contains("+hello");
  }

  @Test
  void largeInputsStillProduceADiff() {
    StringBuilder a = new StringBuilder();
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 6000; i++) {
      a.append("line-").append(i).append('\n');
      b.append("line-").append(i + (i == 3000 ? 100000 : 0)).append('\n');
    }

    String diff = UnifiedDiff.diff(a.toString(), b.toString());

    assertThat(diff).isNotEmpty();
    assertThat(diff).startsWith("--- a/previous");
  }
}
