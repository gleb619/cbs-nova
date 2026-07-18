package cbs.nova.dsl.registry;

import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GeneratedClassProvider;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class GeneratedClassRegistry {

  private final Map<String, GeneratedClassDescriptor> processes = new ConcurrentHashMap<>();
  private final Map<String, GeneratedClassDescriptor> transactions = new ConcurrentHashMap<>();

  public GeneratedClassRegistry() {
    this(Thread.currentThread().getContextClassLoader());
  }

  public GeneratedClassRegistry(ClassLoader classLoader) {
    ServiceLoader.load(GeneratedClassProvider.class, classLoader).forEach(this::register);
  }

  public void register(@NonNull GeneratedClassProvider provider) {
    register(provider.descriptor());
  }

  public void register(@NonNull GeneratedClassDescriptor descriptor) {
    switch (descriptor.type()) {
      case PROCESS -> processes.put(descriptor.name(), descriptor);
      case TRANSACTION -> transactions.put(descriptor.name(), descriptor);
      default -> {
        // Functions are not generated as Temporal classes.
      }
    }
  }

  public @NonNull Optional<GeneratedClassDescriptor> findProcess(@NonNull String name) {
    return Optional.ofNullable(processes.get(name));
  }

  public @NonNull Optional<GeneratedClassDescriptor> findTransaction(@NonNull String name) {
    return Optional.ofNullable(transactions.get(name));
  }

  public @NonNull List<GeneratedClassDescriptor> processes() {
    return List.copyOf(processes.values());
  }

  public @NonNull List<GeneratedClassDescriptor> transactions() {
    return List.copyOf(transactions.values());
  }
}
