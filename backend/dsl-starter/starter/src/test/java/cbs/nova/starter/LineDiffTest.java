package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.VcsModels.DiffHunk;
import cbs.nova.starter.util.LineDiff;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class LineDiffTest {

  @Test
  void identicalTextsProduceNoHunks() {
    LineDiff.Result result = LineDiff.diff("a\nb\nc", "a\nb\nc");

    assertThat(result.hunks()).isEmpty();
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void changedLineProducesSingleHunkWithPrefixes() {
    LineDiff.Result result = LineDiff.diff("a\nb\nc", "a\nB\nc");

    assertThat(result.hunks()).hasSize(1);
    DiffHunk hunk = result.hunks().get(0);
    assertThat(hunk.lines()).containsExactly(" a", "-b", "+B", " c");
    assertThat(hunk.beforeStart()).isEqualTo(0);
    assertThat(hunk.beforeLines()).isEqualTo(3);
    assertThat(hunk.afterStart()).isEqualTo(0);
    assertThat(hunk.afterLines()).isEqualTo(3);
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void appendedLinesProduceAddHunk() {
    LineDiff.Result result = LineDiff.diff("a", "a\nb\nc");

    assertThat(result.hunks()).hasSize(1);
    assertThat(result.hunks().get(0).lines())
            .containsExactly(" a", "+b", "+c");
  }

  @Test
  void insertionAfterPublishLineKeepsSurroundingContext() {
    String before = String.join("\n", "name", "version", "tail-1", "tail-2", "tail-3", "tail-4");
    String after = String.join("\n", "name", "version", "inserted", "tail-1", "tail-2", "tail-3",
            "tail-4");

    LineDiff.Result result = LineDiff.diff(before, after);

    assertThat(result.hunks()).hasSize(1);
    assertThat(result.hunks().get(0).lines())
            .containsExactly(" name", " version", "+inserted", " tail-1", " tail-2", " tail-3");
  }

  @Test
  void moreChangeBlocksThanCapAreTruncated() {
    // 250 single-line changes separated by 10 unchanged lines each: 250 hunks, cap is 200.
    List<String> beforeLines = new ArrayList<>();
    List<String> afterLines = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      for (int k = 0; k < 10; k++) {
        beforeLines.add("same-" + i + "-" + k);
        afterLines.add("same-" + i + "-" + k);
      }
      beforeLines.add("old-" + i);
      afterLines.add("new-" + i);
    }
    beforeLines.add("final");
    afterLines.add("final");

    LineDiff.Result result = LineDiff.diff(String.join("\n", beforeLines),
            String.join("\n", afterLines));

    assertThat(result.hunks()).hasSize(StarterConstants.DEFAULT_MAX_HUNKS);
    assertThat(result.truncated()).isTrue();
  }

  @Test
  void nullAndEmptyInputsAreTreatedAsEmptyText() {
    assertThat(LineDiff.diff(null, "a").hunks()).hasSize(1);
    assertThat(LineDiff.diff("", "").hunks()).isEmpty();
    assertThat(LineDiff.diff("a\n", "a").hunks()).isEmpty();
  }
}
