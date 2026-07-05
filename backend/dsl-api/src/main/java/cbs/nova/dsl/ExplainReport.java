package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String mermaidDiagram,
        @NonNull List<String> executionTrace) {
}
