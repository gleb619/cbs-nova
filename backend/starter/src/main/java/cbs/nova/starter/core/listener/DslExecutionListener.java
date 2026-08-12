package cbs.nova.starter.core.listener;

import cbs.nova.starter.core.event.DslExecutionEvent;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface DslExecutionListener {

  void onEvent(@NonNull DslExecutionEvent event);
}
