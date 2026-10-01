public class KafkaPublishModels {

  public record PublishIn(String topic, String key, String payload) {
  }

  public record PublishOut(String topic, int partition, long offset, java.time.Instant timestamp) {
  }
}