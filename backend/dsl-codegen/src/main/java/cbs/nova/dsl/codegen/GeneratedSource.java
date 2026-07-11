package cbs.nova.dsl.codegen;

import org.jspecify.annotations.NonNull;

public record GeneratedSource(
        @NonNull String packageName, @NonNull String className, @NonNull String source) {

}
