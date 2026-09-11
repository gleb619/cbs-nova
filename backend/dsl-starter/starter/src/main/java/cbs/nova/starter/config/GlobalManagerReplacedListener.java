package cbs.nova.starter.config;

import cbs.nova.dsl.GlobalManager;
import org.jspecify.annotations.NonNull;

/**
 * Callback invoked after the live DSL {@link GlobalManager} singleton has been atomically replaced,
 * e.g. by {@code DslReloadHandler}. Implementations should re-register any state that is not part
 * of the reloaded DSL set (such as Spring-managed {@code @HelperBean} helpers) into the new
 * manager.
 */
@FunctionalInterface
public interface GlobalManagerReplacedListener {

  void onGlobalManagerReplaced(@NonNull GlobalManager globalManager);

}
