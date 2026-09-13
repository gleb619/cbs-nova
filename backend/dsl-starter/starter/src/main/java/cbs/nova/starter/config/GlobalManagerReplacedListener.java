package cbs.nova.starter.config;

import cbs.nova.dsl.GlobalManager;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface GlobalManagerReplacedListener {

  void onGlobalManagerReplaced(@NonNull GlobalManager globalManager);

}
