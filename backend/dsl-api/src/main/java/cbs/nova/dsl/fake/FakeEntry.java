package cbs.nova.dsl.fake;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record FakeEntry(
        @NonNull String type,
        @NonNull String code,
        @Nullable Object response) {
}
