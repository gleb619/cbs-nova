package cbs.nova.dsl.model;

import java.util.List;

/**
 * A single hunk of a line-oriented diff: the 0-based start offset and line count on both sides plus
 * the unified-diff-style prefixed lines (" ", "-", "+").
 */
public record DiffHunk(
        int beforeStart,
        int beforeLines,
        int afterStart,
        int afterLines,
        List<String> lines) {

}
