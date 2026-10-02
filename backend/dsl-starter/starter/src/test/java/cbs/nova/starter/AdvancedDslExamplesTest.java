package cbs.nova.starter;

import cbs.nova.dslexamples.v1.AggregationModels.AggregationIn;
import cbs.nova.dslexamples.v1.AggregationModels.AggregationOut;
import cbs.nova.dslexamples.v1.ConfigValidationModels.ConfigValidationIn;
import cbs.nova.dslexamples.v1.ConfigValidationModels.ConfigValidationOut;
import cbs.nova.dslexamples.v1.DataIntegrityModels.DataIntegrityIn;
import cbs.nova.dslexamples.v1.DataIntegrityModels.DataIntegrityOut;
import cbs.nova.dslexamples.v1.SafeTemplatingModels.SafeTemplatingIn;
import cbs.nova.dslexamples.v1.SafeTemplatingModels.SafeTemplatingOut;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

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
import cbs.nova.dslexamples.v1.SchemaValidationModels.*;
import cbs.nova.dslexamples.v1.ScheduleWindowModels.ScheduleWindowIn;
import cbs.nova.dslexamples.v1.ScheduleWindowModels.ScheduleWindowOut;
import cbs.nova.dslexamples.v1.AuditTrailModels.AuditTrailIn;
import cbs.nova.dslexamples.v1.AuditTrailModels.AuditTrailOut;
import cbs.nova.dslexamples.v1.ContentSanitizeModels.ContentSanitizeIn;
import cbs.nova.dslexamples.v1.ContentSanitizeModels.ContentSanitizeOut;
import cbs.nova.dslexamples.v1.WebhookSignatureModels.WebhookSignatureIn;
import cbs.nova.dslexamples.v1.WebhookSignatureModels.WebhookSignatureOut;
import cbs.nova.dslexamples.v1.ApiKeyProvisioningModels.ApiKeyProvisioningIn;
import cbs.nova.dslexamples.v1.ApiKeyProvisioningModels.ApiKeyProvisioningOut;
import cbs.nova.dslexamples.v1.RecordReconcileModels.RecordReconcileIn;
import cbs.nova.dslexamples.v1.RecordReconcileModels.RecordReconcileOut;
import cbs.nova.dslexamples.v1.SampleDataGenerationModels.SampleDataGenerationIn;
import cbs.nova.dslexamples.v1.SampleDataGenerationModels.SampleDataGenerationOut;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.process.PreviewSignalBuffer;
import cbs.nova.dsl.process.SeededSignal;
import cbs.nova.dslexamples.v1.SignalBranchModels.SignalBranchIn;
import cbs.nova.dslexamples.v1.SignalBranchModels.SignalBranchOut;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.*;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import cbs.nova.dslexamples.v1.DistributedTracingModels.DistributedTracingIn;
import cbs.nova.dslexamples.v1.DistributedTracingModels.DistributedTracingOut;
import cbs.nova.starter.config.properties.OpenTelemetryHelperProperties;
import cbs.nova.starter.helper.OpenTelemetryHelper;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;

class AdvancedDslExamplesTest {

  @RegisterExtension
  static final OpenTelemetryExtension OTEL = OpenTelemetryExtension.create();

  private static final OpenTelemetryHelper OTEL_HELPER = new OpenTelemetryHelper(
          OTEL.getOpenTelemetry(),
          new OpenTelemetryHelperProperties(
                  1000,
                  java.time.Duration.ofMinutes(10),
                  java.time.Duration.ofMinutes(10),
                  1000),
          java.time.Clock.systemUTC());

  @BeforeEach
  void loadCompactDsls() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    DslConfig.dslConfig().expressionEvaluator()
            .replace(new cbs.nova.dsl.utils.MvelExpressionEvaluator());
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
  void schemaValidationPreviewReportsValidInvalidAndFailFast() {
    String schema = "{\"type\":\"object\",\"required\":[\"name\",\"age\"],"
            + "\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"number\"}}}";
    var input = new SchemaValidationIn(
            schema,
            "{\"name\":\"Alice\",\"age\":30}",
            "{\"age\":\"not-a-number\"}",
            "{\"age\":\"not-a-number\"}",
            true);
    Context<SchemaValidationIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("SchemaValidation", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    SchemaValidationOut out = (SchemaValidationOut) result.value();
    assertThat(out.validResult()).isTrue();
    assertThat(out.validErrorCount()).isZero();
    assertThat(out.invalidResult()).isFalse();
    assertThat(out.invalidErrorCount()).isGreaterThanOrEqualTo(2);
    assertThat(out.firstInvalidPath()).isEqualTo("$.name");
    assertThat(out.firstInvalidMessage()).isEqualTo("field is required");
    assertThat(out.failFastResult()).isFalse();
    assertThat(out.failFastErrorCount()).isEqualTo(1);
    assertThat(out.firstFailFastPath()).isEqualTo("$.name");
    assertThat(out.firstFailFastMessage()).isEqualTo("field is required");
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

  @Test
  void webhookSignaturePreviewSignsAndVerifiesAcrossScenarios() {
    var input = new WebhookSignatureIn(
            "{\"event\":\"order.created\",\"id\":\"order-42\"}",
            "test-shared-secret-1",
            "test-shared-secret-2",
            "{\"event\":\"order.created\",\"id\":\"order-99\"}",
            "hex");
    Context<WebhookSignatureIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("WebhookSignature", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    WebhookSignatureOut out = (WebhookSignatureOut) result.value();
    // hex-encoded HMAC-SHA256 is 64 lowercase hex characters; the header prefixes
    // the digest with the "sha256=" scheme tag.
    assertThat(out.outboundSignature()).matches("[0-9a-f]{64}");
    assertThat(out.outboundHeader()).isEqualTo("sha256=" + out.outboundSignature());
    assertThat(out.encoding()).isEqualTo("hex");
    // End-to-end round-trip with the same secret must accept.
    assertThat(out.validSignedPayload()).isTrue();
    // Tampered payload + original signature must reject.
    assertThat(out.validTamperedPayload()).isFalse();
    // Original payload + wrong secret must reject.
    assertThat(out.validWrongSecret()).isFalse();
  }

  @Test
  void auditTrailPreviewStampsUtcAndZoneAwareTimestamps() {
    var input = new AuditTrailIn("ORDER_STATUS_CHANGED", "scheduler", "Asia/Kolkata");
    Context<AuditTrailIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("AuditTrail", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    AuditTrailOut out = (AuditTrailOut) result.value();
    assertThat(out.eventType()).isEqualTo("ORDER_STATUS_CHANGED");
    assertThat(out.actor()).isEqualTo("scheduler");
    // ISO_OFFSET_DATE_TIME in UTC renders with a trailing 'Z' — assert format, not value.
    assertThat(out.utcTimestamp())
            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    // Explicit zone renders as an ISO-8601 offset, e.g. '...T11:30:00+05:30'.
    assertThat(out.localTimestamp())
            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+\\d{2}:\\d{2}");
    // The local timestamp is an offset-converted view of the same instant, so the
    // wall-clock part differs from UTC but the offset reflects the requested zone.
    assertThat(out.zone()).isEqualTo("Asia/Kolkata");
  }

  @Test
  void recordAggregationPreviewAggregatesAndMergesRecords() {
    List<Map<String, Object>> records = List.of(
            Map.of("id", "o-1", "category", "electronics", "amount", 120.50),
            Map.of("id", "o-2", "category", "books", "amount", 45.00),
            Map.of("id", "o-3", "category", "electronics", "amount", 89.99));
    List<Map<String, Object>> mergeRecords = List.of(
            Map.of("id", "order-1",
                    "status", "new",
                    "customer", Map.of("name", "Ada", "tier", "gold"),
                    "tags", List.of("a")),
            Map.of("status", "paid",
                    "customer", Map.of("tier", "platinum"),
                    "note", "rush"));
    var input = new AggregationIn(records, "amount", "category", mergeRecords);
    Context<AggregationIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("RecordAggregation", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    AggregationOut out = (AggregationOut) result.value();

    // sumBy over the amount field; minBy/maxBy return the whole record.
    assertThat(out.total()).isEqualTo(255.49, offset(0.0001));
    assertThat(out.minRecord()).containsEntry("id", "o-2");
    assertThat(out.maxRecord()).containsEntry("id", "o-1");
    assertThat(out.mean()).isEqualTo(85.1633, offset(0.0001));
    assertThat(out.max()).isEqualTo(120.50, offset(0.0001));
    assertThat(out.groups()).containsOnlyKeys("electronics", "books");

    // Shallow merge replaces the nested customer map wholesale (last-write-wins),
    // while deep merge recurses into it and keeps keys only the earlier record has.
    assertThat(out.shallowMerged())
            .containsEntry("id", "order-1")
            .containsEntry("status", "paid")
            .containsEntry("customer", Map.of("tier", "platinum"))
            .containsEntry("note", "rush");
    @SuppressWarnings("unchecked")
    Map<String, Object> deepCustomer = (Map<String, Object>) out.deepMerged().get("customer");
    assertThat(deepCustomer)
            .containsEntry("name", "Ada")
            .containsEntry("tier", "platinum");
    assertThat(out.deepMerged())
            .containsEntry("status", "paid")
            .containsEntry("tags", List.of("a"));
  }

  @Test
  void distributedTracingPreviewRunsAndReturnsTraceparent() {
    var input = new DistributedTracingIn("ORDER-42");
    Context<DistributedTracingIn> ctx = SimpleContext.builder(input)
            .mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("DistributedTracing", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    DistributedTracingOut out = (DistributedTracingOut) result.value();
    assertThat(out.traceparent())
            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    assertThat(out.extractedSpanId()).matches("[0-9a-f]{16}");
    assertThat(out.baggageValue()).isEqualTo("ORDER-42");
  }

  @Test
  void DataIntegrityPreviewCompressesHashesAndEncodesPayload() {
    String payload = "{\"order\":\"42\",\"items\":[\"a\",\"b\"]}";
    var input = new DataIntegrityIn(payload);
    Context<DataIntegrityIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("DataIntegrity", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    DataIntegrityOut out = (DataIntegrityOut) result.value();

    // SHA-256 hex digest is always 64 lowercase hex characters.
    assertThat(out.digest()).matches("[0-9a-f]{64}");
    assertThat(out.encodedPayload()).isNotBlank();

    // The encoded payload is the base64-encoded gzip-compressed bytes.
    String compressedBase64 = new String(
            Base64.getDecoder().decode(out.encodedPayload()), StandardCharsets.UTF_8);
    byte[] compressedBytes = Base64.getDecoder().decode(compressedBase64);
    String recovered;
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
      recovered = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertThat(recovered).isEqualTo(payload);

    assertThat(out.digest()).isEqualTo(sha256Hex(compressedBase64));
  }

  @Test
  void safeTemplatingPreviewDemonstratesInterpolateAndFormatMessageContrast() {
    var order = java.util.Map.<String, Object>of("id", "42");
    var input = new SafeTemplatingIn(order, "Hello {orderId}!");
    Context<SafeTemplatingIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("SafeTemplating", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    SafeTemplatingOut out = (SafeTemplatingOut) result.value();

    assertThat(out.nestedPathResult()).isEqualTo("Order #42 placed");
    assertThat(out.defaultValueResult()).isEqualTo("Priority: standard");
    assertThat(out.escapedLiteralResult()).isEqualTo("Raw token: ${order.total} (kept literal)");
    assertThat(out.missingKeyResult()).isEqualTo("Coupon: ");
    assertThat(out.resolvedKeys()).containsExactly("order.id");
    assertThat(out.trustedGreeting()).isEqualTo("Hello 42!");
  }

  @Test
  void contentSanitizePreviewDecodesEscapesSlugsFormatsAndGroups() {
    // form-encoded payload: "Hello World, world!" -> title, "<b>world</b>" -> body,
    // "tom & jerry > 'top cat'" -> json, group key "category".
    String title = "Hello World";
    String body = "<b>world</b>";
    String meta = "tom & jerry > 'top cat'";
    var input = new ContentSanitizeIn(
            urlEncodeForm(title),
            urlEncodeForm(body),
            urlEncodeForm(meta),
            1_700_000_000_000L,
            "category");
    Context<ContentSanitizeIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ContentSanitize", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ContentSanitizeOut out = (ContentSanitizeOut) result.value();

    // urlDecode (form=true): "+" -> " ", percent escapes recovered.
    assertThat(out.decodedTitle()).isEqualTo(title);
    assertThat(out.decodedBody()).isEqualTo(body);
    assertThat(out.decodedMeta()).isEqualTo(meta);

    // htmlEscape / xmlEscape on the five markup characters; both differ only on the apostrophe.
    assertThat(out.htmlSafeBody()).isEqualTo("&lt;b&gt;world&lt;/b&gt;");
    assertThat(out.xmlSafeMeta()).isEqualTo("tom &amp; jerry &gt; &apos;top cat&apos;");

    // caseConvert (kebab): splits on non-alnum, lowercases, joins with '-'.
    assertThat(out.kebabSlug()).isEqualTo("hello-world");

    // formatDate (ISO_INSTANT, UTC) renders a Z-suffixed instant.
    assertThat(out.isoTimestamp()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");

    // groupRecords on "category" places the single sanitized record under "news".
    assertThat(out.grouped()).containsOnlyKeys("news");
    assertThat(out.grouped().get("news")).hasSize(1);
    java.util.Map<String, Object> only = out.grouped().get("news").get(0);
    assertThat(only).containsEntry("title", title);
    assertThat(only).containsEntry("slug", "hello-world");
    assertThat(only).containsEntry("htmlBody", "&lt;b&gt;world&lt;/b&gt;");
    assertThat(only).containsEntry("xmlMeta", "tom &amp; jerry &gt; &apos;top cat&apos;");
    assertThat(only).containsEntry("category", "news");
  }

  @Test
  void recordReconcilePreviewMatchesCandidateAndDiffsPayload() {
    // Three existing customer records; "Anne-Sophie Müller" must reconcile to "Anne Müller"
    // under jaroWinkler (person-name friendly) and the diff must surface the field-level
    // changes between the matched record's JSON and the candidate JSON.
    List<Map<String, Object>> candidates = List.of(
            Map.of("name", "Jon Doe", "email", "jon@example.com", "tier", "silver"),
            Map.of("name", "Anne Müller", "email", "anne@example.com", "tier", "gold"),
            Map.of("name", "Bob Stone", "email", "bob@example.com", "tier", "bronze"));
    String candidateJson = "{\"name\":\"Anne-Sophie Müller\","
            + "\"email\":\"anne.sophie@example.com\","
            + "\"tier\":\"platinum\"}";
    var input = new RecordReconcileIn(
            "Anne-Sophie Müller",
            candidates,
            candidateJson,
            0.5,
            "jaroWinkler",
            "name");
    Context<RecordReconcileIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("RecordReconcile", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    RecordReconcileOut out = (RecordReconcileOut) result.value();
    assertThat(out.mode()).isEqualTo("jaroWinkler");
    assertThat(out.candidateName()).isEqualTo("Anne-Sophie Müller");
    assertThat(out.matchThreshold()).isEqualTo(0.5);
    assertThat(out.matched()).isTrue();
    assertThat(out.matchedIndex()).isEqualTo(1);
    assertThat(out.bestCandidateName()).isEqualTo("Anne Müller");
    // jaroWinkler >= 0.5 for a clear partial-name match; lower bound leaves room across
    // dependency upgrades of the algorithm, but the result must stay non-trivial.
    assertThat(out.bestScore()).isBetween(0.5, 1.0);
    // jsonDiff sees a structurally similar record: the matched record has 3 keys, the
    // candidate JSON has 3 keys, and at least one of the leaf values differs.
    assertThat(out.identical()).isFalse();
    assertThat(out.changeCount()).isPositive();
    assertThat(out.firstChangeOp()).isIn("changed", "added", "removed");
    assertThat(out.firstChangePath()).startsWith("$.");
  }

  @Test
  void recordReconcilePreviewReportsNoMatchBelowThreshold() {
    // All candidates score too low against "Carol Jones"; no match should be reported
    // and jsonDiff still runs against the empty-object fallback to keep the output shape
    // uniform (the candidate JSON is then reported as fully added).
    List<Map<String, Object>> candidates = List.of(
            Map.of("name", "Jon Doe", "email", "jon@example.com"),
            Map.of("name", "Bob Stone", "email", "bob@example.com"));
    String candidateJson = "{\"name\":\"Carol Jones\",\"email\":\"carol@example.com\"}";
    var input = new RecordReconcileIn(
            "Carol Jones",
            candidates,
            candidateJson,
            0.999,
            "levenshtein",
            "name");
    Context<RecordReconcileIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("RecordReconcile", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    RecordReconcileOut out = (RecordReconcileOut) result.value();
    assertThat(out.mode()).isEqualTo("levenshtein");
    assertThat(out.matched()).isFalse();
    assertThat(out.matchedIndex()).isEqualTo(-1);
    assertThat(out.bestCandidateName()).isEmpty();
    // No match -> matched record is the empty-object fallback, so every candidate
    // JSON field is reported as added.
    assertThat(out.identical()).isFalse();
    assertThat(out.changeCount()).isEqualTo(2);
    assertThat(out.firstChangeOp()).isEqualTo("added");
  }

  @Test
  void dslConfigValidationPreviewParsesValidatesAndFormatsConfig() {
    String configYaml = "service: payment-gateway\n"
            + "version: 2.3.1\n"
            + "releaseDate: \"2026-09-27\"\n"
            + "maxRetries: 12\n";
    String schema = "{\"type\":\"object\","
            + "\"required\":[\"service\",\"version\",\"releaseDate\",\"maxRetries\"],"
            + "\"properties\":{"
            + "\"service\":{\"type\":\"string\"},"
            + "\"version\":{\"type\":\"string\"},"
            + "\"releaseDate\":{\"type\":\"string\"},"
            + "\"maxRetries\":{\"type\":\"number\"}}}";
    var input = new ConfigValidationIn(configYaml, schema);
    Context<ConfigValidationIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ConfigValidation", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ConfigValidationOut out = (ConfigValidationOut) result.value();
    assertThat(out.schemaValid()).isTrue();
    assertThat(out.schemaErrorCount()).isZero();
    assertThat(out.versionFormatValid()).isTrue();
    // ISO_LOCAL_DATE parsed at start-of-day in UTC normalizes to a Z-suffixed instant.
    assertThat(out.parsedReleaseDate()).isEqualTo("2026-09-27T00:00:00Z");
    assertThat(out.formattedThreshold()).isEqualTo("12");
    assertThat(out.canonicalYaml()).contains("service: payment-gateway", "version: 2.3.1");
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
      if (helperClass == SchemaValidateHelper.class) {
        return new SchemaValidateHelper();
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
      if (helperClass == HtmlEscapeHelper.class) {
        return new HtmlEscapeHelper();
      }
      if (helperClass == XmlEscapeHelper.class) {
        return new XmlEscapeHelper();
      }
      if (helperClass == CaseConvertHelper.class) {
        return new CaseConvertHelper();
      }
      if (helperClass == GroupRecordsHelper.class) {
        return new GroupRecordsHelper();
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
      if (helperClass == OpenTelemetryHelper.class) {
        return OTEL_HELPER;
      }
      if (helperClass == InterpolateHelper.class) {
        return new InterpolateHelper();
      }
      if (helperClass == SimilarityHelper.class) {
        return new SimilarityHelper();
      }
      if (helperClass == JsonDiffHelper.class) {
        return new JsonDiffHelper();
      }
      if (helperClass == MetricHelper.class) {
        return new MetricHelper(null);
      }

      try {
        var o = helperClass.getConstructor().newInstance();
        if (o instanceof Executable exe) {
          return exe;
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName());
    };
  }

  @Test
  void signalBranchPreviewResolvesSeededSignalAndBranchesOnPayload() {
    var input = new SignalBranchIn("req-1", 250);
    Context<SignalBranchIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .runId("run-signal-branch")
            .metadata(Map.of(Constants.PREVIEW_SIGNAL_SEEDS_KEY,
                    List.of(new SeededSignal("decision", "{\"approved\":true}", null)),
                    Constants.SIGNAL_AWAITER_METADATA_KEY,
                    new PreviewSignalBuffer(List.of(
                            new SeededSignal("decision", "{\"approved\":true}", null)))))
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("SignalBranch", ctx);

    assertThat(result.isSuccess()).isTrue();
    SignalBranchOut out = (SignalBranchOut) result.value();
    assertThat(out.decision()).isEqualTo("APPROVED:250");
    assertThat(out.requestId()).isEqualTo("req-1");
    assertThat(out.signalSeen()).isTrue();
  }

  @Test
  void signalBranchPreviewTakesRejectBranchOnSeededPayload() {
    var input = new SignalBranchIn("req-2", 10);
    var seed = new SeededSignal("decision", "{\"approved\":false}", null);
    Context<SignalBranchIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .runId("run-signal-branch-reject")
            .metadata(Map.of(Constants.SIGNAL_AWAITER_METADATA_KEY,
                    new PreviewSignalBuffer(List.of(seed))))
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("SignalBranch", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(((SignalBranchOut) result.value()).decision()).isEqualTo("REJECTED:10");
  }

  @Test
  void signalBranchPreviewWithoutBufferStillFailsWithLegacyIllegalState() {
    var input = new SignalBranchIn("req-3", 99);
    Context<SignalBranchIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .runId("run-signal-branch-legacy")
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("SignalBranch", ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("Signal awaiting is not available");
  }

  private static String urlEncodeForm(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String sha256Hex(String value) {
    try {
      byte[] raw = MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : raw) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

}
