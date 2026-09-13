package cbs.nova.dsl.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ValidationIssue(@Nullable String code, @NonNull String message) {

}
