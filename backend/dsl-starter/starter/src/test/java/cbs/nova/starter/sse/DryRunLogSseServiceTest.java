package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.logging.DryRunLogBuffer;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogEvent;
import cbs.nova.starter.service.EmptyObjectProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DryRunLogSseServiceTest {

  private DryRunLogSseService service;
  private DryRunLogSseService cappedService;

  @BeforeEach
  void setUp() {
    service = new DryRunLogSseService(new CbsSseProperties(500, 1000),
            EmptyObjectProvider.of(DryRunLogBufferRegistry.class));
    cappedService = new DryRunLogSseService(new CbsSseProperties(2, 1000),
            EmptyObjectProvider.of(DryRunLogBufferRegistry.class));
  }

  @Test
  void subscribeReturnsEmitterAndReplacesPrevious() {
    SseEmitter first = service.subscribe("run-1", 0L);
    assertThat(first).isNotNull();
    assertThat(service.subscriberCount("run-1")).isEqualTo(1);

    SseEmitter second = service.subscribe("run-1", 0L);
    assertThat(second).isNotSameAs(first);
    assertThat(service.subscriberCount("run-1")).isEqualTo(1);
  }

  @Test
  void reSubscribeSameIdDoesNotConsumeAdditionalCap() {
    cappedService.subscribe("a", 0L);
    cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void subscribeOverCapThrowsAndCreatesNoEmitter() {
    cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);

    assertThatThrownBy(() -> cappedService.subscribe("c", 0L))
            .isInstanceOf(DslSubscriberCapExceededException.class)
            .satisfies(ex -> {
              DslSubscriberCapExceededException e = (DslSubscriberCapExceededException) ex;
              assertThat(e.getCap()).isEqualTo(2);
              assertThat(e.getId()).isEqualTo("c");
            });

    assertThat(cappedService.subscriberCount("c")).isEqualTo(0);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void completeFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    invokeDefaultCallback(a, "completionCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    SseEmitter c = cappedService.subscribe("c", 0L);
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void timeoutFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    invokeDefaultCallback(a, "timeoutCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
  }

  @Test
  void errorFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a", 0L);
    cappedService.subscribe("b", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    Field f = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredField("errorCallback");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    Consumer<Throwable> cb = (Consumer<Throwable>) f.get(a);
    cb.accept(new IOException("client disconnected"));
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
  }

  @Test
  void staleCallbackAfterReplacementDoesNotFreeNewerEmitter() throws Exception {
    SseEmitter first = cappedService.subscribe("a", 0L);
    cappedService.subscribe("a", 0L);
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    invokeDefaultCallback(first, "completionCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
  }

  @Test
  void destroyShutsDownHeartbeatExecutor() throws Exception {
    Field field = DryRunLogSseService.class.getDeclaredField("heartbeatExecutor");
    field.setAccessible(true);
    ScheduledExecutorService exec = (ScheduledExecutorService) field.get(service);
    assertThat(exec.isShutdown()).isFalse();
    service.destroy();
    assertThat(exec.isShutdown()).isTrue();
    service.destroy();
  }

  @Test
  void publishToUnknownRunIsNoOp() {
    service.publish("missing",
            new DryRunLogEvent("INFO", "hello", 1L, Map.of(), "missing"));
    assertThat(service.subscriberCount("missing")).isEqualTo(0);
  }

  @Test
  void publishSendsLogEventToSubscribedRun() throws Exception {
    SseEmitter emitter = service.subscribe("run-1", 0L);
    SseEmitter spy = spy(emitter);
    doNothing().when(spy).send(any(SseEmitter.SseEventBuilder.class));
    replaceEmitter(service, "run-1", spy);

    service.publish("run-1",
            new DryRunLogEvent("INFO", "live line", 42L, Map.of(), "run-1"));

    verify(spy).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void noLastEventIdDoesNotTouchBuffer() {
    DryRunLogBufferRegistry registry = mock(DryRunLogBufferRegistry.class);
    DryRunLogSseService withBuffer = new DryRunLogSseService(new CbsSseProperties(500, 1000),
            providerOf(registry));

    withBuffer.subscribe("trace-1", 0L);

    verify(registry, never()).get(any());
  }

  @Test
  void lastEventIdReplaysBufferEntriesAboveCursorWithTheirIds() throws Exception {
    DryRunLogBuffer buffer = bufferWith(1, "first", "second", "third");
    DryRunLogBufferRegistry registry = mock(DryRunLogBufferRegistry.class);
    when(registry.get("trace-1")).thenReturn(buffer);
    DryRunLogSseService withBuffer = new DryRunLogSseService(new CbsSseProperties(500, 1000),
            providerOf(registry));

    SseEmitter capture = mock(SseEmitter.class);
    withBuffer.subscribe("trace-1", 1L);
    replaceEmitter(withBuffer, "trace-1", capture);
    invokeBackfill(withBuffer, "trace-1", 1L);

    org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> sent = org.mockito.ArgumentCaptor
            .forClass(SseEmitter.SseEventBuilder.class);
    // 2 entries with id > 1.
    verify(capture, org.mockito.Mockito.times(2)).send(sent.capture());
    assertThat(extractId(sent.getAllValues().get(0))).isEqualTo("2");
    assertThat(extractId(sent.getAllValues().get(1))).isEqualTo("3");
  }

  @Test
  void backfillIsNoOpWhenNoBufferRegistered() {
    DryRunLogBufferRegistry registry = mock(DryRunLogBufferRegistry.class);
    when(registry.get("trace-x")).thenReturn(null);
    DryRunLogSseService withBuffer = new DryRunLogSseService(new CbsSseProperties(500, 1000),
            providerOf(registry));

    SseEmitter emitter = withBuffer.subscribe("trace-x", 10L);

    assertThat(emitter).isNotNull();
    assertThat(withBuffer.subscriberCount("trace-x")).isEqualTo(1);
  }

  @Test
  void emptyBackfillDoesNotBreakSubscribe() {
    DryRunLogBuffer buffer = bufferWith(0);
    DryRunLogBufferRegistry registry = mock(DryRunLogBufferRegistry.class);
    when(registry.get("trace-1")).thenReturn(buffer);
    DryRunLogSseService withBuffer = new DryRunLogSseService(new CbsSseProperties(500, 1000),
            providerOf(registry));

    SseEmitter emitter = withBuffer.subscribe("trace-1", 5L);

    assertThat(emitter).isNotNull();
  }

  @Test
  void publishAttachesSequentialIdToLiveLogEvent() throws Exception {
    SseEmitter capture = mock(SseEmitter.class);
    service.subscribe("trace-1", 0L);
    replaceEmitter(service, "trace-1", capture);

    service.publish("trace-1", new DryRunLogEvent("INFO", "line-1", 1L, Map.of(), "trace-1"));
    service.publish("trace-1", new DryRunLogEvent("INFO", "line-2", 2L, Map.of(), "trace-1"));

    org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> sent = org.mockito.ArgumentCaptor
            .forClass(SseEmitter.SseEventBuilder.class);
    verify(capture, org.mockito.Mockito.times(2)).send(sent.capture());
    assertThat(extractId(sent.getAllValues().get(0))).isEqualTo("1");
    assertThat(extractId(sent.getAllValues().get(1))).isEqualTo("2");
  }

  @SuppressWarnings("unchecked")
  private static void replaceEmitter(DryRunLogSseService svc, String id, SseEmitter replacement)
          throws Exception {
    Field field = DryRunLogSseService.class.getDeclaredField("emitters");
    field.setAccessible(true);
    Map<String, DryRunLogSseService.EmitterState> emitters = (Map<String, DryRunLogSseService.EmitterState>) field
            .get(svc);
    emitters.put(id, new DryRunLogSseService.EmitterState(replacement,
            new java.util.concurrent.atomic.AtomicLong(1L)));
  }

  private static <T> ObjectProvider<T> providerOf(T value) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> p = mock(ObjectProvider.class);
    when(p.getIfAvailable()).thenReturn(value);
    return p;
  }

  private static DryRunLogBuffer bufferWith(int startId, String... messages) {
    Deque<DryRunLogBuffer.Entry> queue = new LinkedList<>();
    int nextId = startId;
    for (String m : messages) {
      queue.add(new DryRunLogBuffer.Entry(nextId,
              new DryRunLogEvent("INFO", m, System.currentTimeMillis(), Map.of(), "trace-1")));
      nextId++;
    }
    DryRunLogBuffer buffer = new DryRunLogBuffer(Math.max(messages.length, 1), queue);
    return buffer;
  }

  private static String extractId(SseEmitter.SseEventBuilder builder) throws Exception {
    for (Object dwt : builder.build()) {
      Field dataField = dwt.getClass().getDeclaredField("data");
      dataField.setAccessible(true);
      Object data = dataField.get(dwt);
      if (data instanceof String s) {
        int idx = s.indexOf("id:");
        if (idx < 0) {
          continue;
        }
        int end = s.indexOf('\n', idx);
        String idLine = s.substring(idx, end);
        return idLine.substring(idLine.indexOf(':') + 1).trim();
      }
    }
    throw new IllegalStateException("no id in built frame");
  }

  private static void invokeBackfill(DryRunLogSseService svc, String id, long lastEventId)
          throws Exception {
    Field emittersField = DryRunLogSseService.class.getDeclaredField("emitters");
    emittersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, DryRunLogSseService.EmitterState> emitters = (Map<String, DryRunLogSseService.EmitterState>) emittersField
            .get(svc);
    DryRunLogSseService.EmitterState state = emitters.get(id);
    assertThat(state).isNotNull();
    java.lang.reflect.Method m = DryRunLogSseService.class
            .getDeclaredMethod("backfillAndAdvance", String.class, long.class,
                    DryRunLogSseService.EmitterState.class);
    m.setAccessible(true);
    m.invoke(svc, id, lastEventId, state);
  }

  private static void invokeDefaultCallback(SseEmitter emitter, String fieldName)
          throws Exception {
    Field f = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredField(fieldName);
    f.setAccessible(true);
    Runnable callback = (Runnable) f.get(emitter);
    callback.run();
  }
}
