package cbs.nova.dsl.registry;

import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.ParameterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.NonNull;

public final class DefaultParameterRegistry implements ParameterRegistry {

  private final List<ParameterDescriptor> items = new ArrayList<>();

  @Override
  public @NonNull ParameterRegistry string(@NonNull String name) {
    items.add(ParameterDescriptor.ofString(name));
    return this;
  }

  @Override
  public @NonNull ParameterRegistry number(@NonNull String name) {
    items.add(ParameterDescriptor.ofNumber(name));
    return this;
  }

  @Override
  public @NonNull ParameterRegistry bool(@NonNull String name) {
    items.add(ParameterDescriptor.ofBoolean(name));
    return this;
  }

  @Override
  public @NonNull ParameterRegistry object(@NonNull String name, @NonNull Class<?> type) {
    items.add(ParameterDescriptor.ofObject(name, type));
    return this;
  }

  @Override
  public @NonNull List<ParameterDescriptor> descriptors() {
    return Collections.unmodifiableList(items);
  }
}
