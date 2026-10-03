package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.logging.DryRunLogEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DryRunLogSseServiceTest {

  private DryRunLogSseService service;
  private DryRunLogSseService cappedService;

  @BeforeEach
  void setUp() {
    service = new DryRunLogSseService(new CbsSseProperties(500));
    cappedService = new DryRunLogSseService(new CbsSseProperties(2));
  }

  @Test
  void subscribeReturnsEmitterAndReplacesPrevious() {
    SseEmitter first = service.subscribe("run-1");
    assertThat(first).isNotNull();
    assertThat(service.subscriberCount("run-1")).isEqualTo(1);

    SseEmitter second = service.subscribe("run-1");
    assertThat(second).isNotSameAs(first);
    assertThat(service.subscriberCount("run-1")).isEqualTo(1);
  }

  @Test
  void reSubscribeSameIdDoesNotConsumeAdditionalCap() {
    cappedService.subscribe("a");
    cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void subscribeOverCapThrowsAndCreatesNoEmitter() {
    cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);

    assertThatThrownBy(() -> cappedService.subscribe("c"))
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
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    invokeDefaultCallback(a, "completionCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    SseEmitter c = cappedService.subscribe("c");
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void timeoutFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    invokeDefaultCallback(a, "timeoutCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
  }

  @Test
  void errorFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
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
    SseEmitter first = cappedService.subscribe("a");
    cappedService.subscribe("a");
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
    SseEmitter emitter = service.subscribe("run-1");
    SseEmitter spy = spy(emitter);
    doNothing().when(spy).send(any(SseEmitter.SseEventBuilder.class));
    replaceEmitter(service, "run-1", spy);

    service.publish("run-1",
            new DryRunLogEvent("INFO", "live line", 42L, Map.of(), "run-1"));

    verify(spy).send(any(SseEmitter.SseEventBuilder.class));
  }

  @SuppressWarnings("unchecked")
  private static void replaceEmitter(DryRunLogSseService svc, String id, SseEmitter replacement)
          throws Exception {
    Field field = DryRunLogSseService.class.getDeclaredField("emitters");
    field.setAccessible(true);
    Map<String, SseEmitter> emitters = (Map<String, SseEmitter>) field.get(svc);
    emitters.put(id, replacement);
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
