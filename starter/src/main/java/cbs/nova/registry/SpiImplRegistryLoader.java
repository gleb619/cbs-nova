package cbs.nova.registry;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.ImplRegistrationProvider;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.WritableRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * SPI-based loader for {@link ImplRegistrationProvider} implementations.
 *
 * <p>Discovers all {@link ImplRegistrationProvider} implementations on the classpath via
 * {@link ServiceLoader} and invokes {@link ImplRegistrationProvider#register} on each to populate
 * the {@link DslRegistry} at application startup.
 *
 * <p>Duplicate detection: if two providers register DSL components with the same {@code code}, an
 * {@link IllegalStateException} is thrown with a clear message identifying the conflicting
 * providers.
 */
public final class SpiImplRegistryLoader {

  private SpiImplRegistryLoader() {}

  /**
   * Load all {@link ImplRegistrationProvider} implementations and register them into the given
   * registry.
   *
   * @param registry the {@link DslRegistry} to populate with DSL definitions
   * @throws IllegalStateException if duplicate {@code code} registrations are detected across
   *     providers
   */
  public static void loadInto(DslRegistry registry) {
    Map<String, String> codeToProvider = new HashMap<>();
    ServiceLoader<ImplRegistrationProvider> providers =
        ServiceLoader.load(ImplRegistrationProvider.class);

    for (ImplRegistrationProvider provider : providers) {
      String providerName = provider.getClass().getName();
      DuplicateCheckingRegistry checkingRegistry =
          new DuplicateCheckingRegistry(registry, providerName, codeToProvider);
      provider.register(checkingRegistry);
    }
  }

  /** Wrapper registry that detects duplicate {@code code} registrations across providers. */
  private static class DuplicateCheckingRegistry implements WritableRegistry {

    private final WritableRegistry delegate;
    private final String providerName;
    private final Map<String, String> codeToProvider;

    DuplicateCheckingRegistry(
        WritableRegistry delegate, String providerName, Map<String, String> codeToProvider) {
      this.delegate = delegate;
      this.providerName = providerName;
      this.codeToProvider = codeToProvider;
    }

    @Override
    public void register(TransactionDefinition t) {
      checkDuplicate(t.getCode());
      delegate.register(t);
    }

    @Override
    public void register(HelperDefinition h) {
      checkDuplicate(h.getCode());
      delegate.register(h);
    }

    @Override
    public void register(ConditionDefinition c) {
      checkDuplicate(c.getCode());
      delegate.register(c);
    }

    @Override
    public void register(WorkflowDefinition w) {
      checkDuplicate(w.getCode());
      delegate.register(w);
    }

    @Override
    public void register(MassOperationDefinition m) {
      checkDuplicate(m.getCode());
      delegate.register(m);
    }

    @Override
    public void register(EventDefinition e) {
      checkDuplicate(e.getCode());
      delegate.register(e);
    }

    private void checkDuplicate(String code) {
      String existing = codeToProvider.put(code, providerName);
      if (existing != null && !existing.equals(providerName)) {
        throw new IllegalStateException("Duplicate DSL component code '" + code + "' registered by "
            + existing + " and " + providerName);
      }
    }
  }
}
