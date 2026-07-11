package cbs.nova.dsl;

import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Common contract for diagram generators that can produce visual representations
 * of processes, transactions, and helper activities.
 */
public interface DiagramGenerator {

  @NonNull
  String forProcess(@NonNull ProcessDslObject process);

  @NonNull
  String forProcess(@NonNull ProcessDslObject process,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts);

  @NonNull
  String forTransaction(@NonNull TransactionDslObject tx);

  @NonNull
  String forTransaction(@NonNull TransactionDslObject tx,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts);

  @NonNull
  String forHelper(@NonNull String name);

  @NonNull
  String forHelper(@NonNull String name,
          @Nullable List<Map<String, Object>> externalCalls,
          @Nullable Map<String, Integer> callCounts);
}
