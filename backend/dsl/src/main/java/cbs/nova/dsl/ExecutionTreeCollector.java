package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RunId-scoped nested call-tree collector.
 *
 * <p>
 * Receives start/end events from runners via {@link ExecutionListener} and assembles an immutable
 * tree of {@link CallNode} entries per runId. Designed to be activated by the preview/explain entry
 * points (T150) and consumed once a run finishes.
 */
//TODO: class can cause a memory leak, to remove, change to a new pipe stage
public final class ExecutionTreeCollector implements ExecutionListener {

  private static final Logger log = LoggerFactory.getLogger(ExecutionTreeCollector.class);

  private final int maxDepth;

  private final Map<String, Deque<Frame>> stacks = new ConcurrentHashMap<>();
  private final Map<String, CallNode> roots = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> cycleSets = new ConcurrentHashMap<>();
  private final Map<String, Integer> skipCounts = new ConcurrentHashMap<>();

  public ExecutionTreeCollector() {
    this(32);
  }

  public ExecutionTreeCollector(int maxDepth) {
    this.maxDepth = maxDepth;
  }

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
    cycleSets.remove(runId);
    skipCounts.remove(runId);
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
    if (skipCounts.getOrDefault(runId, 0) > 0) {
      return;
    }
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
      int skipCount = skipCounts.getOrDefault(runId, 0);
      if (skipCount > 0) {
        skipCounts.put(runId, skipCount + 1);
        return;
      }
      if (stack.size() >= maxDepth) {
        stack.push(new Frame("<truncated>", kind, null, true));
        skipCounts.put(runId, 1);
        log.warn("ExecutionTreeCollector: depth limit {} reached at '{}', truncating subtree",
                maxDepth, name);
        return;
      }
      String cycleKey = name + ":" + kind;
      Set<String> cycleSet = cycleSets.computeIfAbsent(runId, k -> new HashSet<>());
      if (cycleSet.contains(cycleKey)) {
        stack.push(new Frame("<truncated>", kind, null, true));
        skipCounts.put(runId, 1);
        log.warn("ExecutionTreeCollector: cycle detected at '{}' ({}), truncating",
                name, kind);
        return;
      }
      stack.push(new Frame(name, kind, input, false));
      cycleSet.add(cycleKey);
    }
  }

  private void popFrame(String runId, String name, Object output, boolean success) {
    Deque<Frame> stack = stacks.get(runId);
    if (stack == null) {
      return;
    }
    synchronized (stack) {
      int skipCount = skipCounts.getOrDefault(runId, 0);
      if (skipCount > 0) {
        skipCount--;
        if (skipCount == 0) {
          Frame sentinel = stack.poll();
          if (sentinel != null && sentinel.sentinel) {
            CallNode node = new CallNode(
                    "<truncated>",
                    sentinel.kind,
                    null, null, false,
                    List.of(), List.of());
            Frame parent = stack.peek();
            if (parent == null) {
              roots.put(runId, node);
            } else {
              parent.children.add(node);
            }
          }
          skipCounts.remove(runId);
        } else {
          skipCounts.put(runId, skipCount);
        }
        return;
      }
      Frame frame = stack.poll();
      if (frame == null || frame.sentinel) {
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
      String cycleKey = frame.name + ":" + frame.kind;
      Set<String> cycleSet = cycleSets.get(runId);
      if (cycleSet != null) {
        cycleSet.remove(cycleKey);
      }
    }
  }

  private static final class Frame {

    final String name;
    final CallKind kind;
    final Object input;
    Object output;
    boolean success;
    final boolean sentinel;
    final List<CallNode> children = new ArrayList<>();
    final List<Map<String, Object>> externalCalls = new ArrayList<>();

    Frame(String name, CallKind kind, Object input, boolean sentinel) {
      this.name = name;
      this.kind = kind;
      this.input = input;
      this.sentinel = sentinel;
    }
  }
}
