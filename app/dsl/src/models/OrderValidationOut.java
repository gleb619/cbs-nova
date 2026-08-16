import io.avaje.jsonb.Json;

@Json
public record OrderValidationOut(boolean valid, String reason) {}
