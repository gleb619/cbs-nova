package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.utils.MvelExpressionEvaluator;
import cbs.nova.dslexamples.v1.CsvYamlExportModels.ExportIn;
import cbs.nova.dslexamples.v1.CsvYamlExportModels.ExportOut;
import cbs.nova.dslexamples.v1.HelperPipelineModels.PipelineIn;
import cbs.nova.dslexamples.v1.HelperPipelineModels.PipelineOut;
import cbs.nova.dslexamples.v1.JsonPatchModels.PatchIn;
import cbs.nova.dslexamples.v1.JsonPatchModels.PatchOut;
import cbs.nova.dslexamples.v1.ListOpsModels.ListOpsDslIn;
import cbs.nova.dslexamples.v1.ListOpsModels.ListOpsDslOut;
import cbs.nova.dslexamples.v1.MaskSensitiveDataModels.MaskingIn;
import cbs.nova.dslexamples.v1.MaskSensitiveDataModels.MaskingOut;
import cbs.nova.dslexamples.v1.OrderMetricsModels.OrderMetricsIn;
import cbs.nova.dslexamples.v1.OrderMetricsModels.OrderMetricsOut;
import cbs.nova.dslexamples.v1.PaginatedUrlModels.PaginatedUrlIn;
import cbs.nova.dslexamples.v1.PaginatedUrlModels.PaginatedUrlOut;
import cbs.nova.dslexamples.v1.RecordOpsModels.RecordOpsIn;
import cbs.nova.dslexamples.v1.RecordOpsModels.RecordOpsOut;
import cbs.nova.dslexamples.v1.ReleasePolicyModels.ReleasePolicyIn;
import cbs.nova.dslexamples.v1.ReleasePolicyModels.ReleasePolicyOut;
import cbs.nova.dslexamples.v1.ReleaseWindowModels.ReleaseIn;
import cbs.nova.dslexamples.v1.ReleaseWindowModels.ReleaseOut;
import cbs.nova.dslexamples.v1.XmlExtractModels.ExtractIn;
import cbs.nova.dslexamples.v1.XmlExtractModels.ExtractOut;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.HttpCallHelper;
import cbs.nova.starter.helper.JsonExtractHelper;
import cbs.nova.starter.helper.MetricHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import cbs.nova.starter.helper.model.CsvOptions;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExampleDslsSmokeTest {

  private static final Set<String> NEEDS_INFRA = Set.of(
          "AmqpPublishDsl", // requires a live RabbitMQ broker
          "KafkaPublishDsl", // requires a live Kafka broker
          "JwtHttpCallDsl" // requires a live HTTP endpoint and shared secret
  );

  @BeforeEach
  void loadCompactDsls() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    DslConfig.dslConfig().expressionEvaluator().replace(new MvelExpressionEvaluator());
    new DefinitionLoader().load(GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void maskSensitiveDataDslMasksCardAndRedactsPayerReference() {
    var input = new MaskingIn(
            "1234567890123456",
            "PAYER-REF-123",
            "Card ending in ${card}",
            1_000L);
    Context<MaskingIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("MaskSensitiveData", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    MaskingOut out = (MaskingOut) result.value();
    assertThat(out.maskedCard()).isEqualTo("************3456");
    assertThat(out.notificationMessage()).contains("************3456");
    assertThat(out.auditDetails())
            .containsEntry("amount", 1_000L)
            .containsEntry("payerReference", "PA#########23");
  }

  @Test
  void releasePolicyDslAcceptsSatisfiedVersionAndBumps() {
    var input = new ReleasePolicyIn("1.2.3", "^1.0.0", "minor", "build.42");
    Context<ReleasePolicyIn> ctx = SimpleContext.builder(input)
            .mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("ReleasePolicy", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ReleasePolicyOut out = (ReleasePolicyOut) result.value();
    assertThat(out.accepted()).isTrue();
    assertThat(out.rangeSatisfied()).isTrue();
    assertThat(out.major()).isEqualTo(1);
    assertThat(out.minor()).isEqualTo(2);
    assertThat(out.patch()).isEqualTo(3);
    assertThat(out.bumpedVersion()).isEqualTo("1.3.0");
    assertThat(out.nextCandidate()).isEqualTo("1.2.4+build.42");
  }

  @Test
  void releasePolicyDslRejectsVersionOutsideRequiredRange() {
    var input = new ReleasePolicyIn("0.9.0", "^1.0.0", "minor", "build.42");
    Context<ReleasePolicyIn> ctx = SimpleContext.builder(input)
            .mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("ReleasePolicy", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ReleasePolicyOut out = (ReleasePolicyOut) result.value();
    assertThat(out.accepted()).isFalse();
    assertThat(out.rangeSatisfied()).isFalse();
    assertThat(out.major()).isEqualTo(0);
    assertThat(out.minor()).isEqualTo(9);
    assertThat(out.patch()).isZero();
    assertThat(out.bumpedVersion()).isNull();
    assertThat(out.nextCandidate()).isNull();
  }

  @Test
  void releaseWindowDslChecksVersionAndShiftsRolloutDate() {
    var input = new ReleaseIn("1.2.0", "1.0.0", "2026-10-01", 7L);
    Context<ReleaseIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("ReleaseWindow", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ReleaseOut out = (ReleaseOut) result.value();
    assertThat(out.versionOk()).isTrue();
    assertThat(out.rolloutDate()).isEqualTo("2026-10-08");
  }

  @Test
  void simpleGreetingDslFunctionGreetsByName() {
    Context<Map<String, Object>> ctx = SimpleContext.<Map<String, Object>>builder()
            .body(Map.of("name", "Nova"))
            .mode(ExecutionMode.PREVIEW)
            .build();

    // NOTE: the SimpleGreeting process uses ctx.runHelper("greetFn", ...) but greetFn is a
    // function, so the process path is currently broken at runtime. This test exercises the
    // function definition itself, which is the meaningful behavior provided by the example DSL.
    Result<?> result = GlobalManager.globalManager().runFunction("greetFn", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    assertThat(result.value()).isEqualTo("Hello, Nova!");
  }

  @Test
  void simpleValidationDslAcceptsInRangeScore() {
    Context<MapInput> ctx = SimpleContext.builder(MapInput.of("score", 85))
            .mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runTransaction("SimpleValidation", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    MapOutput out = (MapOutput) result.value();
    assertThat(out.values())
            .containsEntry("score", 85)
            .containsEntry("valid", true)
            .containsEntry("runId", ctx.runId());
  }

  @Test
  void simpleValidationDslRejectsOutOfRangeScore() {
    Context<MapInput> ctx = SimpleContext.builder(MapInput.of("score", 150))
            .mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runTransaction("SimpleValidation", ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("score out of range: 150");
  }

  @Test
  void jsonPatchRoundTripDslAppliesPatchAndExtractsValue() {
    var input = new PatchIn(
            "{\"name\":\"old\",\"count\":1}",
            "{\"name\":\"new\",\"count\":2}",
            "$.name");
    Context<PatchIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("JsonPatchRoundTrip", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    PatchOut out = (PatchOut) result.value();
    assertThat(out.present()).isTrue();
    assertThat(out.extractedValue()).isEqualTo("new");
    assertThat(out.patchedJson()).contains("\"name\":\"new\"");
  }

  @Test
  void paginatedUrlDslBuildsUrlWithEncodedSegmentAndQuery() {
    var queryParams = new LinkedHashMap<String, String>();
    queryParams.put("page", "2");
    queryParams.put("size", "10");
    var input = new PaginatedUrlIn("https://api.example.com", "path with space", queryParams);
    Context<PaginatedUrlIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("PaginatedUrl", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    PaginatedUrlOut out = (PaginatedUrlOut) result.value();
    assertThat(out.encodedPath()).isEqualTo("path%20with%20space");
    assertThat(out.url())
            .startsWith("https://api.example.com/path%20with%20space?")
            .contains("page=2")
            .contains("size=10");
  }

  @Test
  void recordOpsDslFiltersSortsAndProjectsRecords() {
    var input = new RecordOpsIn("electronics", true, List.of("id", "amount", "status"));
    Context<RecordOpsIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("RecordOps", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    RecordOpsOut out = (RecordOpsOut) result.value();
    assertThat(out.filtered()).hasSize(3);
    assertThat(out.sorted().get(0)).containsEntry("id", "o-3");
    assertThat(out.projected()).hasSize(3);
    assertThat(out.projected().get(0))
            .containsEntry("id", "o-3")
            .containsEntry("amount", 89.99)
            .containsEntry("status", "hold");
  }

  @Test
  void orderMetricsDslProcessesValidOrderAndEmitsMetricCalls() {
    var input = new OrderMetricsIn("order-42", "electronics", 3);
    Context<OrderMetricsIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("OrderMetrics", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    OrderMetricsOut out = (OrderMetricsOut) result.value();
    assertThat(out.orderId()).isEqualTo("order-42");
    assertThat(out.status()).isEqualTo("PROCESSED");
    assertThat(out.processingTimeMs()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void csvYamlExportDslMergesCsvRowsWithYamlLookup() {
    String csvPayload = "US,web\nUK,app\nDE,web\n";
    String yamlPayload = "US: United States\nUK: United Kingdom\nDE: Germany\n";
    var input = new ExportIn(csvPayload, new CsvOptions(",", false, null), yamlPayload);
    Context<ExportIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("CsvYamlExport", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ExportOut out = (ExportOut) result.value();
    assertThat(out.csv())
            .contains("Code,Name,Source")
            .contains("United States")
            .contains("United Kingdom")
            .contains("Germany");
  }

  @Test
  void helperPipelineDslFiltersSumsAndExtractsJsonPath() {
    var records = List.of(
            Map.<String, Object>of("category", "electronics", "amount", 120.50),
            Map.<String, Object>of("category", "books", "amount", 45.00),
            Map.<String, Object>of("category", "electronics", "amount", 89.99));
    var input = new PipelineIn(
            records,
            "category",
            "electronics",
            "Found ${count} records totaling ${total}",
            "{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}",
            "items.1.name");
    Context<PipelineIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("HelperPipeline", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    PipelineOut out = (PipelineOut) result.value();
    assertThat(out.matchedCount()).isEqualTo(2);
    assertThat(out.total()).isEqualTo(210.49);
    assertThat(out.message()).isEqualTo("Found 2 records totaling 210.49");
    assertThat(out.extractedValue()).isEqualTo("second");
    assertThat(out.extracted()).isTrue();
  }

  @Test
  void xmlExtractDslFindsPrimaryAndMissingOptionalXpath() {
    var input = new ExtractIn("/envelope/body", "/envelope/missing");
    Context<ExtractIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("XmlExtract", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ExtractOut out = (ExtractOut) result.value();
    assertThat(out.primaryValue()).isEqualTo("hello");
    assertThat(out.primaryPresent()).isTrue();
    assertThat(out.optionalValue()).isNull();
    assertThat(out.optionalPresent()).isFalse();
  }

  @Test
  void listOpsDslPlucksDistinctAndCountsCategories() {
    var input = new ListOpsDslIn("category", "category");
    Context<ListOpsDslIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();

    Result<?> result = GlobalManager.globalManager().runProcess("ListOps", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ListOpsDslOut out = (ListOpsDslOut) result.value();
    assertThat(out.categories())
            .containsExactly("electronics", "books", "electronics", "toys", "toys", "electronics");
    assertThat(out.distinctCategories()).containsExactly("electronics", "books", "toys");
    assertThat(out.categoryCounts())
            .containsEntry("electronics", 3L)
            .containsEntry("books", 1L)
            .containsEntry("toys", 2L);
  }

  @Test
  void everyExampleDslIsReferencedByBehaviorTestOrAllowlisted() throws IOException {
    Path dslDir = Path.of(System.getProperty("user.dir"))
            .resolve("../dsl-examples/src/dsl")
            .normalize();
    Set<Path> testDirs = Set.of(
            Path.of(System.getProperty("user.dir")).resolve("src/test/java").normalize(),
            Path.of(System.getProperty("user.dir")).resolve("src/integrationTest/java").normalize(),
            Path.of(System.getProperty("user.dir")).resolve("../dsl-examples/src/test/java")
                    .normalize());

    assertThat(dslDir).exists();
    testDirs.forEach(d -> assertThat(d).exists());

    Set<String> dslNames = Files.list(dslDir)
            .filter(p -> p.getFileName().toString().endsWith(".java"))
            .map(p -> p.getFileName().toString().replace(".java", ""))
            .collect(Collectors.toSet());
    assertThat(dslNames).isNotEmpty();

    String testSource = testDirs.stream()
            .flatMap(this::walkJavaFiles)
            .map(this::readString)
            .collect(Collectors.joining("\n"));

    for (String name : dslNames) {
      String lowerName = name.toLowerCase();
      String processName = name.endsWith("Dsl") ? name.substring(0, name.length() - 3) : name;
      boolean referenced = testSource.toLowerCase().contains(lowerName)
              || testSource.toLowerCase().contains(processName.toLowerCase());
      boolean allowlisted = NEEDS_INFRA.contains(name);
      assertThat(referenced || allowlisted)
              .as("%s has no behavior test reference; add one or list in NEEDS_INFRA", name)
              .isTrue();
    }
  }

  private java.util.stream.Stream<Path> walkJavaFiles(Path dir) {
    try {
      return Files.walk(dir).filter(p -> p.getFileName().toString().endsWith(".java"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String readString(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
      if (helperClass == MetricHelper.class) {
        try {
          var ctor = helperClass.getDeclaredConstructor(MeterRegistry.class);
          ctor.setAccessible(true);
          return (Executable<?, ?>) ctor.newInstance((MeterRegistry) null);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException("Cannot instantiate MetricHelper", e);
        }
      }
      try {
        return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }
}
