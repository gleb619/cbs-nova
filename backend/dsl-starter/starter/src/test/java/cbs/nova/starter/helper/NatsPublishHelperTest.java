package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.NatsPublishIn;
import cbs.nova.starter.helper.model.NatsPublishOut;
import io.nats.client.Connection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class NatsPublishHelperTest {

  private final Connection connection = mock(Connection.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final NatsPublishHelper helper = new NatsPublishHelper(connection, mapper);

  @Test
  void publishSendsJsonPayloadToSubject() throws Exception {
    String subject = "orders.created";
    String payload = "{\"id\":42,\"status\":\"confirmed\"}";

    Result<NatsPublishOut> result = execute(new NatsPublishIn(subject, payload));

    assertThat(result.isSuccess()).isTrue();
    NatsPublishOut out = result.value();
    assertThat(out.subject()).isEqualTo(subject);
    assertThat(out.timestamp()).isBetween(Instant.now().minusSeconds(5),
            Instant.now().plusSeconds(5));

    ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(connection).publish(subjectCaptor.capture(), bodyCaptor.capture());
    assertThat(subjectCaptor.getValue()).isEqualTo(subject);
    assertThat(new String(bodyCaptor.getValue(), StandardCharsets.UTF_8)).isEqualTo(payload);
  }

  @Test
  void blankSubjectFails() {
    Result<NatsPublishOut> result = execute(new NatsPublishIn("  ", "{}"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("natsPublish.subject is required");
  }

  @Test
  void invalidJsonPayloadFails() {
    Result<NatsPublishOut> result = execute(new NatsPublishIn("orders", "{not json"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("natsPublish.payload is not valid JSON:");
  }

  @Test
  void missingConnectionBeanFailsWithoutTouchingBroker() {
    NatsPublishHelper withoutConnection = new NatsPublishHelper((Connection) null, mapper);

    Result<NatsPublishOut> result = withoutConnection.execute(
            SimpleContext.builder(new NatsPublishIn("orders", "{}"))
                    .mode(ExecutionMode.PREVIEW).build());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalStateException.class);
    assertThat(result.cause())
            .hasMessage("natsPublish: Connection bean is not available; NATS is not configured");
  }

  private Result<NatsPublishOut> execute(NatsPublishIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
