package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dslexamples.v1.PricingModels.CheckoutReceipt;
import cbs.nova.dslexamples.v1.PricingModels.OrderIn;
import cbs.nova.dslexamples.v1.PricingModels.OrderLine;
import cbs.nova.dslexamples.v1.PricingModels.QuoteOut;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.HttpCallHelper;
import cbs.nova.starter.helper.JsonExtractHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;

/**
 * Exercises the {@code Function} construct example {@code PricingFunctionsDsl} end-to-end in
 * preview mode: both consumers ({@code CheckoutProcess} and {@code QuoteTransaction}) reuse the
 * same {@code orderPricingFn} function, which chains {@code lineTotalFn} and the {@code math}
 * helper.
 *
 * <p>
 * Numbers for the VIP input below (lines 4x2.50 + 2x12.00 + 3x0.75): subtotal=36.25, VIP discount
 * 10% = 3.625 -> rounded 3.63, taxable=32.62, tax 20% = 6.524 -> rounded 6.52, total=39.14.
 */
class PricingFunctionsDslTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void loadCompactDsls() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    new DefinitionLoader().load(GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void checkoutProcessAndQuoteTransactionShareOrderPricingFn() {
    var input = new OrderIn("order-42", "VIP", List.of(
            new OrderLine("widget", 4, 2.50),
            new OrderLine("gadget", 2, 12.00),
            new OrderLine("cable", 3, 0.75)));

    Result<?> processResult = GlobalManager.globalManager()
            .runProcess("CheckoutProcess", contextFactory.of(input, ExecutionMode.PREVIEW));

    assertThat(processResult.isSuccess()).as("cause: %s", processResult.cause()).isTrue();
    CheckoutReceipt receipt = (CheckoutReceipt) processResult.value();
    assertThat(receipt.orderId()).isEqualTo("order-42");
    assertThat(receipt.receiptNumber()).isEqualTo("RCPT-order-42");
    assertThat(receipt.subtotal()).isEqualTo(36.25);
    assertThat(receipt.discountAmount()).isCloseTo(3.63, offset(0.001));
    assertThat(receipt.tax()).isCloseTo(6.52, offset(0.001));
    assertThat(receipt.total()).isCloseTo(39.14, offset(0.001));

    Result<?> transactionResult = GlobalManager.globalManager()
            .runTransaction("QuoteTransaction", contextFactory.of(input, ExecutionMode.PREVIEW));

    assertThat(transactionResult.isSuccess()).as("cause: %s", transactionResult.cause()).isTrue();
    QuoteOut quote = (QuoteOut) transactionResult.value();
    assertThat(quote.orderId()).isEqualTo("order-42");
    assertThat(quote.quoteRef()).isEqualTo("QUOTE-order-42");
    assertThat(quote.subtotal()).isEqualTo(36.25);
    assertThat(quote.discountAmount()).isCloseTo(3.63, offset(0.001));
    assertThat(quote.tax()).isCloseTo(6.52, offset(0.001));
    assertThat(quote.total()).isCloseTo(39.14, offset(0.001));
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      if (helperClass == HttpCallHelper.class) {
        return new HttpCallHelper(HttpClient.newHttpClient(),
                new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true));
      }
      if (helperClass == JsonExtractHelper.class) {
        return new JsonExtractHelper(new ObjectMapper());
      }
      try {
        return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }
}
