package cbs.dsl.api;

import cbs.dsl.api.context.MassOperationContext;

import java.util.List;
import java.util.Map;

/** Defines a data source for mass operations — loads items to process. */
public interface SourceDefinition {

  /**
   * Loads items from the source. Each item is a map of field names to values.
   *
   * @param ctx the mass operation context
   * @return the loaded items
   */
  List<Map<String, Object>> load(MassOperationContext ctx);
}
