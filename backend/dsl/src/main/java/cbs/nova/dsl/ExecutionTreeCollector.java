package cbs.nova.dsl;

import cbs.nova.dsl.transaction.TransactionExecution;
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

public final class ExecutionTreeCollector implements ExecutionListener {

  private static final Logger log = LoggerFactory.getLogger(ExecutionTreeCollector.class);

  private final int maxDepth;

  private final Deque<Frame> stack = new ArrayDeque<>();
  private CallNode root;
  private final Set<String> cycleSet = new HashSet<>();
  private int skipCount;
  private boolean active;

  public ExecutionTreeCollector() {
    this(32);
  }

  public ExecutionTreeCollector(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public void start() {
    synchronized (stack) {
      stack.clear();
      active = true;
    }
    root = null;
    cycleSet.clear();
    skipCount = 0;
  }

  public void finish() {
    synchronized (stack) {
      while (!stack.isEmpty()) {
        stack.pop();
      }
      active = false;
    }
  }

  @Override
  public void onProcessStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
    pushFrame(name, CallKind.PROCESS, input);
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
    popFrame(name, output, success);
  }

  @Override
  public void onTransactionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    pushFrame(name, CallKind.TRANSACTION, input);
  }

  @Override
  public void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(name, output, success);
  }

  @Override
  public void onHelperStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
    pushFrame(name, CallKind.HELPER, input);
  }

  @Override
  public void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(name, output, success);
  }

  @Override
  public void onFunctionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    pushFrame(name, CallKind.FUNCTION, input);
  }

  @Override
  public void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    popFrame(name, output, success);
  }

  public void attachExternalCall(@NonNull Map<String, Object> call) {
    if (!active || skipCount > 0) {
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

  public @NonNull Optional<CallNode> tree() {
    return Optional.ofNullable(root);
  }

  private void pushFrame(String name, CallKind kind, Object input) {
    synchronized (stack) {
      if (!active) {
        return;
      }
      if (skipCount > 0) {
        skipCount++;
        return;
      }
      if (stack.size() >= maxDepth) {
        stack.push(new Frame("<truncated>", kind, null, true));
        skipCount = 1;
        log.warn("ExecutionTreeCollector: depth limit {} reached at '{}', truncating subtree",
                maxDepth, name);
        return;
      }
      String cycleKey = name + ":" + kind;
      if (cycleSet.contains(cycleKey)) {
        stack.push(new Frame("<truncated>", kind, null, true));
        skipCount = 1;
        log.warn("ExecutionTreeCollector: cycle detected at '{}' ({}), truncating",
                name, kind);
        return;
      }
      stack.push(new Frame(name, kind, input, false));
      cycleSet.add(cycleKey);
    }
  }

  private void popFrame(String name, Object output, boolean success) {
    synchronized (stack) {
      if (!active) {
        return;
      }
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
              root = node;
            } else {
              parent.children.add(node);
            }
          }
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
        root = node;
      } else {
        parent.children.add(node);
      }
      String cycleKey = frame.name + ":" + frame.kind;
      cycleSet.remove(cycleKey);
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
