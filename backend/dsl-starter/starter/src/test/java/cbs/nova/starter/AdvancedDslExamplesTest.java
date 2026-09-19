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
import cbs.nova.dslexamples.v1.OrderSagaModels.OrderSagaIn;
import cbs.nova.dslexamples.v1.OrderSagaModels.OrderSagaOut;
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
