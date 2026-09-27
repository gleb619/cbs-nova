package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.AmqpPublishIn;
import cbs.nova.starter.helper.model.AmqpPublishOut;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

class AmqpPublishHelperTest {

  private final AmqpTemplate amqpTemplate = mock(AmqpTemplate.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final AmqpPublishHelper helper = new AmqpPublishHelper(amqpTemplate, mapper);

  @Test
  void publishSendsJsonPayloadToExchangeAndRoutingKey() {
    String exchange = "orders.topic";
    String routingKey = "order.created";
    String payload = "{\"id\":42,\"status\":\"confirmed\"}";

    Result<AmqpPublishOut> result = execute(new AmqpPublishIn(exchange, routingKey, payload));

    assertThat(result.isSuccess()).isTrue();
    AmqpPublishOut out = result.value();
    assertThat(out.routingKey()).isEqualTo(routingKey);
    assertThat(out.timestamp()).isBetween(Instant.now().minusSeconds(5),
            Instant.now().plusSeconds(5));

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(amqpTemplate).send(eq(exchange), eq(routingKey), messageCaptor.capture());
    Message message = messageCaptor.getValue();
    assertThat(new String(message.getBody())).isEqualTo(payload);
    assertThat(message.getMessageProperties().getContentType())
            .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
  }

  @Test
  void invalidJsonPayloadFails() {
    Result<AmqpPublishOut> result = execute(
            new AmqpPublishIn("orders.topic", "order.created", "{not json"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("amqpPublish.payload is not valid JSON:");
  }

  @Test
  void missingExchangeFails() {
    Result<AmqpPublishOut> result = execute(new AmqpPublishIn("", "order.created", "{}"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("amqpPublish.exchange is required");
  }

  @Test
  void missingRoutingKeyFails() {
    Result<AmqpPublishOut> result = execute(new AmqpPublishIn("orders.topic", null, "{}"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("amqpPublish.routingKey is required");
  }

  @Test
  void missingPayloadFails() {
    Result<AmqpPublishOut> result = execute(new AmqpPublishIn("orders.topic", "order.created", ""));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("amqpPublish.payload is required");
  }

  private Result<AmqpPublishOut> execute(AmqpPublishIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
