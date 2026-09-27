import cbs.nova.dslexamples.AmqpPublishModels.*;
import cbs.nova.starter.helper.model.AmqpPublishIn;
import cbs.nova.starter.helper.model.AmqpPublishOut;

List<DslObject> define() {
  return Dsl.process("AmqpPublish")
      .input(PublishIn.class)
      .output(PublishOut.class)
      .description("Publishes a JSON payload to a RabbitMQ exchange via the amqpPublish helper and returns the routing key and publish timestamp.")
      .execute(ctx -> {
        PublishIn in = ctx.body();

        var published = ctx.runHelper("amqpPublish",
            new AmqpPublishIn(in.exchange(), in.routingKey(), in.payload()));
        if (!published.isSuccess()) {
          return Result.failure(published.cause());
        }
        AmqpPublishOut out = published.as(AmqpPublishOut.class);

        return Result.success(new PublishOut(out.routingKey(), out.timestamp()));
      })
      .buildList();
}
