package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CallNode(
        @NonNull String name,
        @NonNull CallKind kind,
        @Nullable Object input,
        @Nullable Object output,
        boolean success,
        @NonNull List<CallNode> children,
        @NonNull List<Map<String, Object>> externalCalls) {

  public static @NonNull CallNode leaf(
          @NonNull String name,
          @NonNull CallKind kind,
          @Nullable Object input,
          @Nullable Object output,
          boolean success) {
    return new CallNode(name, kind, input, output, success, List.of(), List.of());
  }

  public static @NonNull CallNode node(
          @NonNull String name,
          @NonNull CallKind kind,
          @Nullable Object input,
          @Nullable Object output,
          boolean success,
          @NonNull List<CallNode> children,
          @NonNull List<Map<String, Object>> externalCalls) {
    return new CallNode(name, kind, input, output, success, children, externalCalls);
  }
}
