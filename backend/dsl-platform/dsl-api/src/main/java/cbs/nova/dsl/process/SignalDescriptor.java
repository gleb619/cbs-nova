package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;

public record SignalDescriptor(
        @NonNull String name,
        @NonNull Class<?> payloadType) {
}
