package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String mermaidDiagram,
        @NonNull String plantUmlDiagram,
        @NonNull String bpmnXml,
        @NonNull List<String> executionTrace,
        @NonNull List<Map<String, Object>> externalCalls,
        @NonNull Map<String, Integer> callCounts) {
}
