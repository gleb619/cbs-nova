package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.AmqpPublishIn;
import cbs.nova.starter.helper.model.AmqpPublishOut;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes an arbitrary JSON payload to a RabbitMQ exchange/routing key.
 *
 * <p>
 * Reuses the existing {@link AmqpTemplate} Spring bean; no extra broker connection is created. The
 * helper validates that {@code payload} is valid JSON before sending it with content-type
 * {@code application/json}.
 */
@SpringHelper(name = "amqpPublish")
public class AmqpPublishHelper implements Executable<AmqpPublishIn, AmqpPublishOut> {

  private final @Nullable AmqpTemplate amqpTemplate;
  private final @NonNull ObjectMapper mapper;

  @Autowired
  public AmqpPublishHelper(ObjectProvider<AmqpTemplate> amqpTemplateProvider,
          ObjectMapper mapper) {
    this.amqpTemplate = amqpTemplateProvider.getIfAvailable();
    this.mapper = mapper;
  }

  AmqpPublishHelper(@Nullable AmqpTemplate amqpTemplate, ObjectMapper mapper) {
    this.amqpTemplate = amqpTemplate;
    this.mapper = mapper;
  }

  @Override
  public @NonNull Result<AmqpPublishOut> execute(@NonNull Context<AmqpPublishIn> ctx) {
    try {
      AmqpPublishIn input = ctx.body();
      validate(input);

      if (amqpTemplate == null) {
        return Result.failure(new IllegalStateException(
                "amqpPublish: AmqpTemplate bean is not available; RabbitMQ is not configured"));
      }

      byte[] body = input.payload().getBytes(StandardCharsets.UTF_8);
      MessageProperties properties = MessagePropertiesBuilder.newInstance()
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      amqpTemplate.send(input.exchange(), input.routingKey(), new Message(body, properties));

      return Result.success(new AmqpPublishOut(input.routingKey(), Instant.now()));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private void validate(@NonNull AmqpPublishIn input) {
    if (input.exchange() == null || input.exchange().isBlank()) {
      throw new IllegalArgumentException("amqpPublish.exchange is required");
    }
    if (input.routingKey() == null || input.routingKey().isBlank()) {
      throw new IllegalArgumentException("amqpPublish.routingKey is required");
    }
    if (input.payload() == null || input.payload().isBlank()) {
      throw new IllegalArgumentException("amqpPublish.payload is required");
    }
    try {
      mapper.readTree(input.payload());
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "amqpPublish.payload is not valid JSON: " + e.getOriginalMessage(), e);
    }
  }
}
