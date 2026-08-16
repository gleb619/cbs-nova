import io.avaje.jsonb.Json;
import java.math.BigDecimal;

@Json
public record OrderIn(String customerId, BigDecimal amount, String productId) {}
