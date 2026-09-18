package cbs.nova.dsl;

import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.dsl.model.PreviewReport;
import org.jspecify.annotations.NonNull;

public interface DslRuntime {

  @NonNull
  Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx);

  @NonNull
  Result<HierarchyReport> hierarchy(@NonNull String name, @NonNull Context<?> ctx);

  @NonNull
  Result<?> run(@NonNull String name, @NonNull Context<?> ctx);

  @NonNull
  ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx);
}
