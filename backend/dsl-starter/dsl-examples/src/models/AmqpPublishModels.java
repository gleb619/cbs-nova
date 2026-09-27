public class AmqpPublishModels {

  public record PublishIn(String exchange, String routingKey, String payload) {
  }

  public record PublishOut(String routingKey, java.time.Instant timestamp) {
  }
}
