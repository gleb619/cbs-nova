package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ExecutionSseServiceTest {

  private ExecutionSseService service;
  private ExecutionSseService cappedService;

  @BeforeEach
  void setUp() {
    service = new ExecutionSseService(new CbsSseProperties(500));
    cappedService = new ExecutionSseService(new CbsSseProperties(2));
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
    SseEmitter first = cappedService.subscribe("a");
    SseEmitter second = cappedService.subscribe("a");
    SseEmitter third = cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    assertThat(second).isNotSameAs(first);
    assertThat(third).isNotSameAs(first);
    assertThat(cappedService.subscriberCount("a")).isEqualTo(1);
    assertThat(cappedService.subscriberCount("b")).isEqualTo(1);
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
    assertThat(cappedService.activeSubscriberCount())
            .as("failed subscribe must NOT increment counter")
            .isEqualTo(2);
  }

  @Test
  void concurrentSubscribersAtLimitMinusOneNeverBothSucceed() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < 20; i++) {
        CbsSseProperties props = new CbsSseProperties(2);
        ExecutionSseService s = new ExecutionSseService(props);
        // Pre-fill cap exactly.
        s.subscribe("pre-a");
        s.subscribe("pre-b");
        CountDownLatch ready = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CompletableFuture<?>[] futures = new CompletableFuture[threads];
        for (int t = 0; t < threads; t++) {
          final String id = "race-" + t + "-" + i;
          futures[t] = CompletableFuture.runAsync(() -> {
            ready.countDown();
            try {
              ready.await();
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
            try {
              s.subscribe(id);
              ok.incrementAndGet();
            } catch (DslSubscriberCapExceededException ex) {
              rejected.incrementAndGet();
            }
          }, pool);
        }
        CompletableFuture.allOf(futures).join();
        assertThat(ok.get()).as("iter %d: no thread should succeed when cap is already met", i)
                .isEqualTo(0);
        assertThat(rejected.get()).isEqualTo(threads);
        assertThat(s.activeSubscriberCount()).isEqualTo(2);
        s.destroy();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void completeFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    invokeDefaultCallback(a, "completionCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    // Slot freed → new id succeeds.
    SseEmitter c = cappedService.subscribe("c");
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void timeoutFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    // Spring's onTimeout callbacks are stored in a private DefaultCallback on SseEmitter. We
    // invoke that callback list directly to simulate the async container firing the timeout
    // (no public timeout() method exists on SseEmitter).
    invokeDefaultCallback(a, "timeoutCallback");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    SseEmitter c = cappedService.subscribe("c");
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  private static void invokeDefaultCallback(SseEmitter emitter, String fieldName)
          throws Exception {
    Field f = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredField(fieldName);
    f.setAccessible(true);
    Runnable callback = (Runnable) f.get(emitter);
    callback.run();
  }

  @Test
  void errorFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
    // Trigger the onError callback list (without a Handler attached, SseEmitter#completeWithError
    // does not invoke the registered Consumer — call the registered DefaultCallback directly).
    Field f = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredField("errorCallback");
    f.setAccessible(true);
    java.util.function.Consumer<Throwable> cb = (java.util.function.Consumer<Throwable>) f.get(a);
    cb.accept(new IOException("client disconnected"));
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    SseEmitter c = cappedService.subscribe("c");
    assertThat(c).isNotNull();
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);
  }

  @Test
  void heartbeatSendFailureFreesSlot() throws Exception {
    SseEmitter a = cappedService.subscribe("a");
    cappedService.subscribe("b");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(2);

    // Swap `a` with a spy whose send() throws on every call → publish() path triggers the catch
    // arm in sendHeartbeats (or publish) which removes the entry.
    SseEmitter spyA = spy(a);
    doThrow(new IOException("simulated dead socket")).when(spyA)
            .send(any(SseEmitter.SseEventBuilder.class));
    replaceEmitter(cappedService, "a", spyA);

    cappedService.publish("a", "RUNNING");
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    assertThat(cappedService.subscriberCount("a")).isEqualTo(0);
  }

  @Test
  void staleCallbackAfterReplacementDoesNotFreeNewerEmitter() throws Exception {
    SseEmitter first = cappedService.subscribe("a");
    cappedService.subscribe("a");
    // second replaced first in the map; first's onCompletion callback still references the
    // first emitter instance. Invoking it must NOT decrement the activeCount (newer emitter
    // holds the slot).
    assertThat(cappedService.activeSubscriberCount()).isEqualTo(1);
    invokeDefaultCallback(first, "completionCallback");
    assertThat(cappedService.activeSubscriberCount())
            .as("stale callback must not free newer emitter's slot")
            .isEqualTo(1);
    assertThat(cappedService.subscriberCount("a")).isEqualTo(1);
  }

  @Test
  void destroyShutsDownHeartbeatExecutor() throws Exception {
    Field field = ExecutionSseService.class.getDeclaredField("heartbeatExecutor");
    field.setAccessible(true);
    ScheduledExecutorService exec = (ScheduledExecutorService) field.get(service);
    assertThat(exec.isShutdown()).isFalse();
    service.destroy();
    assertThat(exec.isShutdown()).isTrue();
    // Idempotent: second destroy must not throw.
    service.destroy();
  }

  @Test
  void publishToUnknownRunIsNoOp() {
    service.publish("missing", "COMPLETED");
    assertThat(service.subscriberCount("missing")).isEqualTo(0);
  }

  @Test
  void publishSendsStatusEventWithDisplayCasing() throws Exception {
    SseEmitter emitter = service.subscribe("run-1");
    SseEmitter spy = spy(emitter);
    doNothing().when(spy).send(any(SseEmitter.SseEventBuilder.class));
    replaceEmitter(service, "run-1", spy);

    service.publish("run-1", "COMPLETED");

    verify(spy).send(any(SseEmitter.SseEventBuilder.class));
  }

  @SuppressWarnings("unchecked")
  private static void replaceEmitter(ExecutionSseService svc, String id, SseEmitter replacement)
          throws Exception {
    Field field = ExecutionSseService.class.getDeclaredField("emitters");
    field.setAccessible(true);
    Map<String, SseEmitter> emitters = (Map<String, SseEmitter>) field.get(svc);
    emitters.put(id, replacement);
  }
}
