import cbs.nova.dslexamples.KafkaPublishModels.*;
import cbs.nova.starter.helper.model.KafkaPublishIn;
import cbs.nova.starter.helper.model.KafkaPublishOut;

List<DslObject> define() {
  return Dsl.process("KafkaPublish")
      .input(PublishIn.class)
      .output(PublishOut.class)
      .description("Publishes a JSON payload to a Kafka topic via the kafkaPublish helper and returns the broker-assigned partition and offset.")
      .execute(ctx -> {
        PublishIn in = ctx.body();

        var published = ctx.runHelper("kafkaPublish",
            new KafkaPublishIn(in.topic(), in.key(), in.payload()));
        if (!published.isSuccess()) {
          return Result.failure(published.cause());
        }
        KafkaPublishOut out = published.as(KafkaPublishOut.class);

        return Result.success(new PublishOut(out.topic(), out.partition(), out.offset(), out.timestamp()));
      })
      .buildList();
}