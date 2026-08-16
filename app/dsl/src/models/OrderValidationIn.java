import io.avaje.jsonb.Json;
import java.math.BigDecimal;

@Json
public record OrderValidationIn(String customerId, BigDecimal amount) {}
