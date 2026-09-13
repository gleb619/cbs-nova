package cbs.nova.dsl.history;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record DslRunSearchResult(@NonNull List<DslRun> items, int total) {
}
