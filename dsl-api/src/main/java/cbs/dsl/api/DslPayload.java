package cbs.dsl.api;

import java.util.Map;

/**
 * Contract for typed DSL payload records that can be converted to/from a generic {@code Map<String,
 * Object>} envelope.
 *
 * <p>Implementation records are serialized to JSON (via avaje jsonb) and then deserialized as a Map
 * for transport through the generic {@code TransactionInput}, {@code HelperInput}, etc. wrappers.
 * Conversely, incoming Map parameters are serialized to JSON and deserialized into the typed
 * record.
 */
public interface DslPayload {

  /**
   * Converts this typed record to a plain {@code Map<String, Object>}.
   *
   * @return the map representation
   */
  Map<String, Object> toMap();
}
