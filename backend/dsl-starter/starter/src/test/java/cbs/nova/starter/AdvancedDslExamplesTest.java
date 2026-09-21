package cbs.nova.starter;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dslexamples.v1.ExceptionProbeModels.ExceptionProbeIn;
import cbs.nova.dslexamples.v1.ExceptionProbeModels.ExceptionProbeOut;
import cbs.nova.dslexamples.v1.NestedCompensationModels.NestedCompensationIn;
import cbs.nova.dslexamples.v1.OrderIdGenerationModels.OrderIdGenerationIn;
import cbs.nova.dslexamples.v1.OrderIdGenerationModels.OrderIdGenerationOut;
import cbs.nova.dslexamples.v1.OrderSagaModels.OrderSagaIn;
import cbs.nova.dslexamples.v1.OrderSagaModels.OrderSagaOut;
import cbs.nova.dslexamples.v1.RetryPolicyModels.RetryPolicyIn;
import cbs.nova.dslexamples.v1.RetryPolicyModels.RetryPolicyOut;
import cbs.nova.dslexamples.v1.ScheduleWindowModels.ScheduleWindowIn;
import cbs.nova.dslexamples.v1.ScheduleWindowModels.ScheduleWindowOut;
import cbs.nova.dslexamples.v1.ApiKeyProvisioningModels.ApiKeyProvisioningIn;
import cbs.nova.dslexamples.v1.ApiKeyProvisioningModels.ApiKeyProvisioningOut;
import cbs.nova.dslexamples.v1.SampleDataGenerationModels.SampleDataGenerationIn;
import cbs.nova.dslexamples.v1.SampleDataGenerationModels.SampleDataGenerationOut;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.*;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

class AdvancedDslExamplesTest {

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
  void orderSagaPreviewCompletesSuccessfully() {
    var input = new OrderSagaIn("order1", 2);
    Context<OrderSagaIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("OrderSaga", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(OrderSagaOut.class);
  }

  @Test
  void exceptionProbePreviewSucceedsWhenHelperSucceeds() {
    var input = new ExceptionProbeIn(false, null);
    Context<ExceptionProbeIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ExceptionProbe", ctx);

    assertThat(result.isSuccess()).isTrue();
    ExceptionProbeOut out = (ExceptionProbeOut) result.value();
    assertThat(out.result()).isEqualTo("SUCCESS");
  }

  @Test
  void exceptionProbePreviewFailsWhenHelperFails() {
    var input = new ExceptionProbeIn(true, "test fail");
    Context<ExceptionProbeIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ExceptionProbe", ctx);

    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void nestedCompensationPreviewFailsAtStep3() {
    var input = new NestedCompensationIn("job1");
    Context<NestedCompensationIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("NestedCompensation", ctx);

    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void orderIdGenerationPreviewProducesRandomAndDeterministicIds() {
    var input = new OrderIdGenerationIn("order-42", "orders/v1");
    Context<OrderIdGenerationIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("OrderIdGeneration", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    OrderIdGenerationOut out = (OrderIdGenerationOut) result.value();
    assertThat(out.orderId()).isEqualTo("order-42");
    assertThat(out.randomId()).isNotBlank();
    assertThat(out.namespacedId1()).isNotBlank();
    assertThat(out.namespacedId2()).isNotBlank();
    assertThat(out.deterministicTailMatch()).isTrue();
    String tail1 = out.namespacedId1().substring(out.namespacedId1().lastIndexOf('-') + 1);
    String tail2 = out.namespacedId2().substring(out.namespacedId2().lastIndexOf('-') + 1);
    assertThat(tail1).isEqualTo(tail2);
  }

  @Test
  void retryPolicyPreviewComputesDeterministicAndRandomDelays() {
    var input = new RetryPolicyIn(6, 1000L, 60000L);
    Context<RetryPolicyIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("RetryPolicy", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    RetryPolicyOut out = (RetryPolicyOut) result.value();
    assertThat(out.maxAttempts()).isEqualTo(6);
    assertThat(out.baseMillis()).isEqualTo(1000L);
    assertThat(out.maxMillis()).isEqualTo(60000L);
    assertThat(out.noneDelays()).containsExactly(1000L, 2000L, 4000L, 8000L, 16000L, 32000L);
    assertThat(out.fullDelay()).as("randomized full-jitter delay within [0, cap]")
            .isBetween(0L, 60000L);
  }

  @Test
  void sampleDataGenerationPreviewProducesSyntheticRecord() {
    var input = new SampleDataGenerationIn("ORD");
    Context<SampleDataGenerationIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("SampleDataGeneration", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    SampleDataGenerationOut out = (SampleDataGenerationOut) result.value();
    assertThat(out.orderId()).startsWith("ORD-").hasSize("ORD-".length() + 8);
    assertThat(out.customerId()).isBetween(10000, 99999);
    assertThat(out.amount()).isBetween(10.0, 1000.0);
    assertThat(out.priority()).isIn("low", "medium", "high");
    assertThat(out.region()).isIn("EU", "US", "APAC");
    assertThat(out.tags()).hasSize(3);
    assertThat(out.tags()).allSatisfy(tag -> assertThat(tag).hasSize(6).matches("[0-9a-f]+"));
  }

  @Test
  void apiKeyProvisioningPreviewProducesCryptographicKeys() {
    var input = new ApiKeyProvisioningIn("payment-service");
    Context<ApiKeyProvisioningIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ApiKeyProvisioning", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ApiKeyProvisioningOut out = (ApiKeyProvisioningOut) result.value();
    assertThat(out.purpose()).isEqualTo("payment-service");
    assertThat(out.apiKey()).hasSize(32).matches("[A-Za-z0-9_-]{32}");
    assertThat(out.idempotencyKey()).hasSize(32).matches("[0-9a-f]{32}");
  }

  @Test
  void scheduleWindowPreviewParsesIsoGracePeriodAndShorthandHardLimit() {
    var input = new ScheduleWindowIn("nightly-rollout", "PT1H30M", "2h");
    Context<ScheduleWindowIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ScheduleWindow", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ScheduleWindowOut out = (ScheduleWindowOut) result.value();
    assertThat(out.jobName()).isEqualTo("nightly-rollout");
    // ISO-8601 form: "PT1H30M" = 1h30m = 90 minutes = 5_400_000 ms.
    assertThat(out.graceMillis()).isEqualTo(5_400_000L);
    assertThat(out.graceSeconds()).isEqualTo(5400L);
    assertThat(out.graceIso()).isEqualTo("PT1H30M");
    // Shorthand form: "2h" = 2 hours = 7_200_000 ms.
    assertThat(out.hardLimitMillis()).isEqualTo(7_200_000L);
    assertThat(out.hardLimitSeconds()).isEqualTo(7200L);
    assertThat(out.hardLimitIso()).isEqualTo("PT2H");
  }

  @Test
  void scheduleWindowPreviewParsesShorthandGracePeriodAndIsoHardLimit() {
    var input = new ScheduleWindowIn("batch-flush", "45m", "P2DT3H");
    Context<ScheduleWindowIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ScheduleWindow", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ScheduleWindowOut out = (ScheduleWindowOut) result.value();
    assertThat(out.jobName()).isEqualTo("batch-flush");
    // Shorthand form: "45m" = 45 minutes = 2_700_000 ms.
    assertThat(out.graceMillis()).isEqualTo(2_700_000L);
    assertThat(out.graceSeconds()).isEqualTo(2700L);
    assertThat(out.graceIso()).isEqualTo("PT45M");
    // ISO-8601 form: "P2DT3H" = 2 days + 3 hours = 51 hours = 183_600_000 ms.
    // Bare-day forms normalize to a time component, so iso becomes "PT51H".
    assertThat(out.hardLimitMillis()).isEqualTo(183_600_000L);
    assertThat(out.hardLimitSeconds()).isEqualTo(183_600L);
    assertThat(out.hardLimitIso()).isEqualTo("PT51H");
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == ConditionalFailingHelper.class) {
        return new ConditionalFailingHelper();
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      if (helperClass == CurrentTimestampHelper.class) {
        return new CurrentTimestampHelper();
      }
      if (helperClass == FileLatchHelper.class) {
        return new FileLatchHelper();
      }
      if (helperClass == FilterRecordsHelper.class) {
        return new FilterRecordsHelper();
      }
      if (helperClass == FormatMessageHelper.class) {
        return new FormatMessageHelper();
      }
      if (helperClass == FormatNumberHelper.class) {
        return new FormatNumberHelper();
      }
      if (helperClass == HttpCallHelper.class) {
        return new HttpCallHelper(HttpClient.newHttpClient(),
                new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true));
      }
      if (helperClass == JsonExtractHelper.class) {
        return new JsonExtractHelper(new ObjectMapper());
      }
      if (helperClass == SortRecordsHelper.class) {
        return new SortRecordsHelper();
      }
      if (helperClass == ParseYamlHelper.class) {
        return new ParseYamlHelper();
      }
      if (helperClass == FormatCsvHelper.class) {
        return new FormatCsvHelper();
      }
      if (helperClass == BackoffHelper.class) {
        return new BackoffHelper();
      }
      if (helperClass == CompressionHelper.class) {
        return new CompressionHelper();
      }
      if (helperClass == DateMathHelper.class) {
        return new DateMathHelper();
      }
      if (helperClass == FormatYamlHelper.class) {
        return new FormatYamlHelper();
      }
      if (helperClass == HexHelper.class) {
        return new HexHelper();
      }
      if (helperClass == HttpAuthHelper.class) {
        return new HttpAuthHelper();
      }
      if (helperClass == JsonPatchHelper.class) {
        return new JsonPatchHelper();
      }
      if (helperClass == JwtHelper.class) {
        return new JwtHelper();
      }
      if (helperClass == ListOpsHelper.class) {
        return new ListOpsHelper();
      }
      if (helperClass == ParseCsvHelper.class) {
        return new ParseCsvHelper();
      }
      if (helperClass == QueryStringHelper.class) {
        return new QueryStringHelper();
      }
      if (helperClass == RandomHelper.class) {
        return new RandomHelper();
      }
      if (helperClass == SemverHelper.class) {
        return new SemverHelper();
      }
      if (helperClass == ValidateJsonHelper.class) {
        return new ValidateJsonHelper();
      }
      if (helperClass == XmlExtractHelper.class) {
        return new XmlExtractHelper();
      }
      if (helperClass == ArithmeticHelper.class) {
        return new ArithmeticHelper();
      }
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      if (helperClass == UuidV7Helper.class) {
        return new UuidV7Helper();
      }
      if (helperClass == FormatDateHelper.class) {
        return new FormatDateHelper();
      }
      if (helperClass == ParseDateHelper.class) {
        return new ParseDateHelper();
      }
      if (helperClass == ParseDurationHelper.class) {
        return new ParseDurationHelper();
      }
      if (helperClass == PickHelper.class) {
        return new PickHelper();
      }
      if (helperClass == Base64Helper.class) {
        return new Base64Helper();
      }
      if (helperClass == RegexHelper.class) {
        return new RegexHelper();
      }
      if (helperClass == HmacSha256SignHelper.class) {
        return new HmacSha256SignHelper();
      }
      if (helperClass == HmacSha256VerifyHelper.class) {
        return new HmacSha256VerifyHelper();
      }
      if (helperClass == UrlEncodeHelper.class) {
        return new UrlEncodeHelper();
      }
      if (helperClass == UrlDecodeHelper.class) {
        return new UrlDecodeHelper();
      }

      if (helperClass == Sha256Helper.class) {
        return new Sha256Helper();
      }
      if (helperClass == SecretHelper.class) {
        return new SecretHelper();
      }
      if (helperClass == MaskHelper.class) {
        return new MaskHelper();
      }
      if (helperClass == InterpolateHelper.class) {
        return new InterpolateHelper();
      }
      if (helperClass == MetricHelper.class) {
        return new MetricHelper(null);
      }

      throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName());
    };
  }
}
