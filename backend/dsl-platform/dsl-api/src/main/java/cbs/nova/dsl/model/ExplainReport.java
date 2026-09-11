package cbs.nova.dsl.model;

import org.jspecify.annotations.NonNull;

public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String mermaid) {

}
