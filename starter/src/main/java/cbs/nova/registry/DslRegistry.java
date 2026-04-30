package cbs.nova.registry;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.WritableRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime registry that maps string codes to DSL definition instances.
 *
 * <p>This is the single runtime registry for the execution engine. It is populated at startup by
 * loading all {@link cbs.dsl.api.ImplRegistrationProvider} implementations via SPI (ServiceLoader),
 * which register {@code @DslComponent} annotated classes discovered at compile time.
 *
 * <p>Lookup is by exact match on {@code code}. Registration is additive — registering a definition
 * with the same code as an existing entry overwrites it (last-write-wins). This supports test
 * overrides of production beans.
 */
public class DslRegistry implements WritableRegistry {

  private final Map<String, WorkflowDefinition> workflows = new HashMap<>();
  private final Map<String, EventDefinition> events = new HashMap<>();
  private final Map<String, TransactionDefinition> transactions = new HashMap<>();
  private final Map<String, MassOperationDefinition> massOperations = new HashMap<>();
  private final Map<String, HelperDefinition> helpers = new HashMap<>();
  private final Map<String, ConditionDefinition> conditions = new HashMap<>();

  @Override
  public void register(WorkflowDefinition w) {
    registerChecked(workflows, w.getCode(), w);
  }

  @Override
  public void register(EventDefinition e) {
    registerChecked(events, e.getCode(), e);
  }

  @Override
  public void register(TransactionDefinition t) {
    registerChecked(transactions, t.getCode(), t);
  }

  @Override
  public void register(MassOperationDefinition m) {
    registerChecked(massOperations, m.getCode(), m);
  }

  @Override
  public void register(HelperDefinition h) {
    registerChecked(helpers, h.getCode(), h);
  }

  @Override
  public void register(ConditionDefinition c) {
    registerChecked(conditions, c.getCode(), c);
  }

  public Map<String, WorkflowDefinition> getWorkflows() {
    return Collections.unmodifiableMap(workflows);
  }

  public Map<String, EventDefinition> getEvents() {
    return Collections.unmodifiableMap(events);
  }

  public Map<String, TransactionDefinition> getTransactions() {
    return Collections.unmodifiableMap(transactions);
  }

  public Map<String, MassOperationDefinition> getMassOperations() {
    return Collections.unmodifiableMap(massOperations);
  }

  public Map<String, HelperDefinition> getHelpers() {
    return Collections.unmodifiableMap(helpers);
  }

  public Map<String, ConditionDefinition> getConditions() {
    return Collections.unmodifiableMap(conditions);
  }

  /**
   * Resolve a transaction by code.
   *
   * @param code the transaction code
   * @return the transaction definition
   * @throws IllegalArgumentException if not found
   */
  public TransactionDefinition resolveTransaction(String code) {
    TransactionDefinition t = transactions.get(code);
    if (t == null) {
      throw new IllegalArgumentException("Transaction '" + code + "' not found in DslRegistry");
    }
    return t;
  }

  /**
   * Resolve a helper by code.
   *
   * @param code the helper code
   * @return the helper definition
   * @throws IllegalArgumentException if not found
   */
  public HelperDefinition resolveHelper(String code) {
    HelperDefinition h = helpers.get(code);
    if (h == null) {
      throw new IllegalArgumentException("Helper '" + code + "' not found in DslRegistry");
    }
    return h;
  }

  /**
   * Resolve a condition by code.
   *
   * @param code the condition code
   * @return the condition definition
   * @throws IllegalArgumentException if not found
   */
  public ConditionDefinition resolveCondition(String code) {
    ConditionDefinition c = conditions.get(code);
    if (c == null) {
      throw new IllegalArgumentException("Condition '" + code + "' not found in DslRegistry");
    }
    return c;
  }

  /**
   * Resolve a workflow by code.
   *
   * @param code the workflow code
   * @return the workflow definition
   * @throws IllegalArgumentException if not found
   */
  public WorkflowDefinition resolveWorkflow(String code) {
    WorkflowDefinition w = workflows.get(code);
    if (w == null) {
      throw new IllegalArgumentException("Workflow '" + code + "' not found in DslRegistry");
    }
    return w;
  }

  /**
   * Resolve an event by code.
   *
   * @param code the event code
   * @return the event definition
   * @throws IllegalArgumentException if not found
   */
  public EventDefinition resolveEvent(String code) {
    EventDefinition e = events.get(code);
    if (e == null) {
      throw new IllegalArgumentException("Event '" + code + "' not found in DslRegistry");
    }
    return e;
  }

  /**
   * Resolve a mass operation by code.
   *
   * @param code the mass operation code
   * @return the mass operation definition
   * @throws IllegalArgumentException if not found
   */
  public MassOperationDefinition resolveMassOperation(String code) {
    MassOperationDefinition m = massOperations.get(code);
    if (m == null) {
      throw new IllegalArgumentException("MassOperation '" + code + "' not found in DslRegistry");
    }
    return m;
  }

  private <T> void registerChecked(Map<String, T> map, String code, T value) {
    if (map.containsKey(code)) {
      throw new IllegalStateException("Duplicate registration for code '" + code + "'");
    }
    map.put(code, value);
  }
}
