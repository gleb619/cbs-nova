package cbs.nova.starter.core.listener;

import cbs.nova.starter.core.event.DslExecutionEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

//TODO: we need to add a dslpipe stage impl, that uses a `DslExecutionListener`/`DslExecutionEventBus` thing
public final class DslExecutionEventBus {

  private final List<DslExecutionListener> listeners = new CopyOnWriteArrayList<>();

  public void register(@NonNull DslExecutionListener listener) {
    listeners.add(listener);
  }

  public void publish(@NonNull DslExecutionEvent event) {
    for (DslExecutionListener listener : listeners) {
      try {
        listener.onEvent(event);
      } catch (Exception ignored) {
      }
    }
  }
}
