package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.logging.DryRunLogEvent;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DryRunLogSseServiceTest {

  private DryRunLogSseService service;

  @BeforeEach
  void setUp() {
    service = new DryRunLogSseService();
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
    replaceEmitter("run-1", spy);

    service.publish("run-1",
            new DryRunLogEvent("INFO", "live line", 42L, Map.of(), "run-1"));

    verify(spy).send(any(SseEmitter.SseEventBuilder.class));
  }

  @SuppressWarnings("unchecked")
  private void replaceEmitter(String id, SseEmitter replacement) throws Exception {
    Field field = DryRunLogSseService.class.getDeclaredField("emitters");
    field.setAccessible(true);
    Map<String, SseEmitter> emitters = (Map<String, SseEmitter>) field.get(service);
    emitters.put(id, replacement);
  }
}
