package cbs.nova.starter.core.listener;

import cbs.nova.starter.core.event.DslExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public final class DslExecutionEventBus {

  private final List<DslExecutionListener> listeners = new CopyOnWriteArrayList<>();

  public void register(@NonNull DslExecutionListener listener) {
    listeners.add(listener);
  }

  public void publish(@NonNull DslExecutionEvent event) {
    for (DslExecutionListener listener : listeners) {
      try {
        listener.onEvent(event);
      } catch (Exception ex) {
        log.debug("DSL execution listener threw", ex);
      }
    }
  }
}
