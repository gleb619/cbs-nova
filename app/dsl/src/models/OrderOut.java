import io.avaje.jsonb.Json;
import java.math.BigDecimal;

@Json
public record OrderOut(String orderId, String status, BigDecimal amount) {}
