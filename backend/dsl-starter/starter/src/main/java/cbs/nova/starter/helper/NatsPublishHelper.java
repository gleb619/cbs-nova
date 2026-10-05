package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.NatsPublishIn;
import cbs.nova.starter.helper.model.NatsPublishOut;
import io.nats.client.Connection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes an arbitrary JSON payload to a NATS Core subject.
 *
 * <p>
 * Reuses an injected NATS {@link Connection}; no extra broker connection is created. The helper
 * validates that {@code payload} is valid JSON before sending it as UTF-8 bytes.
 *
 * <p>
 * Opt-in: {@code io.nats:jnats} is a {@code compileOnly} dependency of the starter. Apps that do
 * not declare it and expose a {@code Connection} bean see an explicit {@code IllegalStateException}
 * instead of an NPE.
 */
@SpringHelper(name = "natsPublish", description = "Publishes an arbitrary JSON payload to a NATS Core subject.")
public class NatsPublishHelper implements Executable<NatsPublishIn, NatsPublishOut> {

  private static final Logger LOG = LoggerFactory.getLogger(NatsPublishHelper.class);
  private static final String DISABLED_MESSAGE = "natsPublish: Connection bean is not available; NATS is not configured";

  private final @Nullable Connection connection;
  private final @NonNull ObjectMapper mapper;

  @Autowired
  public NatsPublishHelper(ObjectProvider<Connection> connectionProvider, ObjectMapper mapper) {
    this(connectionProvider.getIfAvailable(), mapper);
  }

  NatsPublishHelper(@Nullable Connection connection, ObjectMapper mapper) {
    this.connection = connection;
    this.mapper = mapper;
  }

  @Override
  public @NonNull Result<NatsPublishOut> execute(@NonNull Context<NatsPublishIn> ctx) {
    try {
      NatsPublishIn input = ctx.body();
      validate(input);

      if (connection == null) {
        LOG.warn(DISABLED_MESSAGE);
        return Result.failure(new IllegalStateException(DISABLED_MESSAGE));
      }

      byte[] body = input.payload().getBytes(StandardCharsets.UTF_8);
      connection.publish(input.subject(), body);

      return Result.success(new NatsPublishOut(input.subject(), Instant.now()));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private void validate(@NonNull NatsPublishIn input) {
    if (input.subject() == null || input.subject().isBlank()) {
      throw new IllegalArgumentException("natsPublish.subject is required");
    }
    if (input.payload() == null || input.payload().isBlank()) {
      throw new IllegalArgumentException("natsPublish.payload is required");
    }
    try {
      mapper.readTree(input.payload());
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "natsPublish.payload is not valid JSON: " + e.getOriginalMessage(), e);
    }
  }
}
