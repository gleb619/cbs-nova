package cbs.nova.dsl;

import cbs.nova.dsl.DslObject.DslType;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Drilldown describing what a {@link DslDefinitionLoader} registered during a load/reload.
 * <p>
 * Replaces the bare {@code int} count the loader used to return so callers (reload admin
 * endpoint, workbench publish, logs) can surface <em>what</em> was loaded — the counts and
 * names per {@link DslType}, not just a total.
 *
 * @param processes    the {@code PROCESS} definition names that were registered
 * @param transactions the {@code TRANSACTION} definition names that were registered
 * @param functions    the {@code FUNCTION} definition names that were registered
 */
public record LoadResult(
        @NonNull List<String> processes,
        @NonNull List<String> transactions,
        @NonNull List<String> functions) {

    public int total() {
        return processes.size() + transactions.size() + functions.size();
    }

    public int processCount() {
        return processes.size();
    }

    public int transactionCount() {
        return transactions.size();
    }

    public int functionCount() {
        return functions.size();
    }

    public static @NonNull LoadResult empty() {
        return new LoadResult(List.of(), List.of(), List.of());
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    /** Mutable accumulator used by {@link DslDefinitionLoader} implementations. */
    public static final class Builder {

        private final List<String> processes = new ArrayList<>();
        private final List<String> transactions = new ArrayList<>();
        private final List<String> functions = new ArrayList<>();

        private Builder() {
        }

        public @NonNull Builder add(@NonNull DslType type, @NonNull String name) {
            switch (type) {
                case PROCESS -> processes.add(name);
                case TRANSACTION -> transactions.add(name);
                case FUNCTION -> functions.add(name);
            }
            return this;
        }

        /** Appends every entry of {@code other}, preserving order — used to combine load phases. */
        public @NonNull Builder merge(@NonNull LoadResult other) {
            processes.addAll(other.processes());
            transactions.addAll(other.transactions());
            functions.addAll(other.functions());
            return this;
        }

        public @NonNull LoadResult build() {
            return new LoadResult(
                    List.copyOf(processes), List.copyOf(transactions), List.copyOf(functions));
        }
    }
}