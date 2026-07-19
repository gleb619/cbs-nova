package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunId-scoped nested call-tree collector.
 *
 * <p>
 * Receives start/end events from runners via {@link ExecutionListener} and assembles an immutable
 * tree of {@link CallNode} entries per runId. Designed to be activated by the preview/explain entry
 * points (T150) and consumed once a run finishes.
 */
public final class ExecutionTreeCollector implements ExecutionListener {

  private final Map<String, Deque<Frame>> stacks = new ConcurrentHashMap<>();
  private final Map<String, CallNode> roots = new ConcurrentHashMap<>();

  /**
   * Prepare a fresh stack for the given runId. Any previously stored root for this runId is
   * discarded.
   */
  public void startRun(@NonNull String runId) {
    stacks.put(runId, new ArrayDeque<>());
    roots.remove(runId);
  }

  /**
   * Tear down the stack for the given runId. Any in-progress frames are popped defensively so that
   * callers may safely abandon a run.
   */
  public void finishRun(@NonNull String runId) {
    Deque<Frame> stack = stacks.remove(runId);
    if (stack == null) {
      return;
    }
    synchronized (stack) {
      while (!stack.isEmpty()) {
        stack.pop();
      }
    }
  }

  @Override
  public void onProcessStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
    pushFrame(runId, name, CallKind.PROCESS, input);
  }

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
    // Intentionally ignored: tree assembly is driven by start/end events.
  }

  @Override
  public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause) {
    // Intentionally ignored: tree assembly is driven by start/end events.
  }

  @Override
  public void onProcessEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(runId, name, output, success);
  }

  @Override
  public void onTransactionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    pushFrame(runId, name, CallKind.TRANSACTION, input);
  }

  @Override
  public void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(runId, name, output, success);
  }

  @Override
  public void onHelperStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
    pushFrame(runId, name, CallKind.HELPER, input);
  }

  @Override
  public void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(runId, name, output, success);
  }

  @Override
  public void onFunctionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    pushFrame(runId, name, CallKind.FUNCTION, input);
  }

  @Override
  public void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(runId, name, output, success);
  }

  /**
   * Append a captured external call (e.g. JDBC/HTTP) to the currently active frame. Silently
   * ignored when no runId stack exists or the stack is empty.
   */
  public void attachExternalCall(@NonNull String runId, @NonNull Map<String, Object> call) {
    Deque<Frame> stack = stacks.get(runId);
    if (stack == null) {
      return;
    }
    synchronized (stack) {
      Frame top = stack.peek();
      if (top == null) {
        return;
      }
      top.externalCalls.add(call);
    }
  }

  /** Returns the assembled root tree for the given runId, if any. */
  public @NonNull Optional<CallNode> tree(@NonNull String runId) {
    return Optional.ofNullable(roots.get(runId));
  }

  private void pushFrame(String runId, String name, CallKind kind, Object input) {
    Deque<Frame> stack = stacks.get(runId);
    if (stack == null) {
      return;
    }
    synchronized (stack) {
      stack.push(new Frame(name, kind, input));
    }
  }

  private void popFrame(String runId, String name, Object output, boolean success) {
    Deque<Frame> stack = stacks.get(runId);
    if (stack == null) {
      return;
    }
    synchronized (stack) {
      Frame frame = stack.poll();
      if (frame == null) {
        return;
      }
      frame.output = output;
      frame.success = success;
      CallNode node = new CallNode(
              frame.name,
              frame.kind,
              frame.input,
              frame.output,
              frame.success,
              List.copyOf(frame.children),
              List.copyOf(frame.externalCalls));
      Frame parent = stack.peek();
      if (parent == null) {
        roots.put(runId, node);
      } else {
        parent.children.add(node);
      }
    }
  }

  private static final class Frame {

    final String name;
    final CallKind kind;
    final Object input;
    Object output;
    boolean success;
    final List<CallNode> children = new ArrayList<>();
    final List<Map<String, Object>> externalCalls = new ArrayList<>();

    Frame(String name, CallKind kind, Object input) {
      this.name = name;
      this.kind = kind;
      this.input = input;
    }
  }
}
