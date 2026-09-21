public class WebhookSignatureModels {

  public record WebhookSignatureIn(
          String payload,
          String secret,
          String wrongSecret,
          String tamperedPayload,
          String encoding) {
  }

  public record WebhookSignatureOut(
          String outboundSignature,
          String outboundHeader,
          boolean validSignedPayload,
          boolean validTamperedPayload,
          boolean validWrongSecret,
          String encoding) {
  }
}
