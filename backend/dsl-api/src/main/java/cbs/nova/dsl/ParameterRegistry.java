package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public interface ParameterRegistry {

  @NonNull
  ParameterRegistry string(@NonNull String name);

  @NonNull
  ParameterRegistry number(@NonNull String name);

  @NonNull
  ParameterRegistry bool(@NonNull String name);

  @NonNull
  ParameterRegistry object(@NonNull String name, @NonNull Class<?> type);

  @NonNull
  List<ParameterDescriptor> descriptors();
}
