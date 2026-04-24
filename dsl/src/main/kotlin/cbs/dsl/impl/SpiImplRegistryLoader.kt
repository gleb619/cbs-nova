package cbs.dsl.impl

import cbs.dsl.api.ImplRegistrationProvider
import cbs.dsl.api.WritableRegistry
import java.util.ServiceLoader

/**
 * SPI-based loader for [ImplRegistrationProvider] implementations.
 *
 * Discovers all [ImplRegistrationProvider] implementations on the classpath via
 * [java.util.ServiceLoader] and invokes [ImplRegistrationProvider.register] on each to populate the
 * [ImplRegistry] at application startup.
 *
 * Duplicate detection: if two providers register DSL components with the same [code], an
 * [IllegalStateException] is thrown with a clear message identifying the conflicting providers.
 */
object SpiImplRegistryLoader {

  /**
   * Load all [ImplRegistrationProvider] implementations and register them into the given registry.
   *
   * @param registry the [ImplRegistry] to populate with DSL definitions
   * @throws IllegalStateException if duplicate [code] registrations are detected across providers
   */
  fun loadInto(registry: ImplRegistry) {
    val codeToProvider = mutableMapOf<String, String>()
    val providers = ServiceLoader.load(ImplRegistrationProvider::class.java)

    for (provider in providers) {
      val providerName = provider::class.java.name
      val checkingRegistry = DuplicateCheckingRegistry(registry, providerName, codeToProvider)
      provider.register(checkingRegistry)
    }
  }

  /**
   * Wrapper registry that detects duplicate [code] registrations across providers.
   *
   * Shares a single [codeToProvider] map across all provider invocations to detect when two
   * different providers attempt to register DSL components with the same code.
   */
  private class DuplicateCheckingRegistry(
      private val delegate: WritableRegistry,
      private val providerName: String,
      private val codeToProvider: MutableMap<String, String>,
  ) : WritableRegistry {

    override fun register(t: cbs.dsl.api.TransactionDefinition) {
      checkDuplicate(t.code)
      delegate.register(t)
    }

    override fun register(h: cbs.dsl.api.HelperDefinition) {
      checkDuplicate(h.code)
      delegate.register(h)
    }

    override fun register(c: cbs.dsl.api.ConditionDefinition) {
      checkDuplicate(c.code)
      delegate.register(c)
    }

    override fun register(w: cbs.dsl.api.WorkflowDefinition) {
      checkDuplicate(w.code)
      delegate.register(w)
    }

    override fun register(m: cbs.dsl.api.MassOperationDefinition) {
      checkDuplicate(m.code)
      delegate.register(m)
    }

    private fun checkDuplicate(code: String) {
      val existing = codeToProvider.put(code, providerName)
      if (existing != null && existing != providerName) {
        throw IllegalStateException(
            "Duplicate DSL component code '$code' registered by $existing and $providerName"
        )
      }
    }
  }
}
