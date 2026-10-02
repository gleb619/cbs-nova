# How to work with DSL examples

The `backend/dsl-starter/dsl-examples` module contains real-world DSL definitions. They are written as
[JEP-512 compact source files](https://openjdk.org/jeps/512) and compiled into Temporal
workflows/activities at build time.

> Java 25 is required for building. Every Gradle wrapper in the repo pins Gradle 9.4.1
> (`./gradlew`, `backend/gradlew`, `backend/dsl-{platform,starter,plugins}/gradlew`,
> `app/{server,dsl}/gradlew`); the examples commands below use
> `backend/dsl-platform/gradlew`.

## Where the examples live

```
backend/dsl-starter/dsl-examples/src/
├── dsl/                      # Compact DSL definitions (the `define()` entry points)
│   ├── BatchProcessingDsl.java
│   ├── ExceptionProbeDsl.java
│   ├── InvoiceGenerationDsl.java
│   ├── LongWorkSimulationDsl.java
│   ├── PricingFunctionsDsl.java
│   ├── SimpleGreetingDsl.java
│   ├── SimpleValidationDsl.java
│   └── ... (run `ls backend/dsl-starter/dsl-examples/src/dsl/` for the current set — 42 files)
└── models/                   # Typed records (@Json / Avaje Jsonb) shared by the DSL sources
    └── ... (one `*Models.java` per process/transaction that declares typed I/O)
```

Every file in `src/dsl/` exposes a `List<DslObject> define()` method built with the fluent DSL API; the
companion `src/models/` records are imported by the compact sources (e.g. `import cbs.nova.dslexamples.BatchModels.*;`).

## Building the examples

The `compileDsl` Gradle task (registered by the `cbs.nova.dsl` plugin) scans
`backend/dsl-starter/dsl-examples/src/dsl/`, loads the definitions, validates them,
and generates Temporal classes under `backend/dsl-starter/dsl-examples/build/generated`.

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :dsl-examples:compileDsl
```

After a successful run you will find generated classes such as:

```
backend/dsl-starter/dsl-examples/build/generated/cbs/nova/dslexamples/v1/
├── BatchProcessingProcessWorkflow.java
└── BatchProcessingProcessDefinition.java
```

The generated package is `cbs.nova.dslexamples.<version>` — `cbs.nova.dslexamples` is the
module's `dslCompile { dslPackage = '...' }` base, and `<version>` is the version declared in the DSL (default `v1`). Generated class names embed the process/transaction name.

## Running the integration test

`backend/dsl-starter/starter` hosts the example integration tests in its `integrationTest` source set
(`backend/dsl-starter/starter/src/integrationTest/java/cbs/nova/dsl/example/integration/`).
The Testcontainers-based suite starts a real Temporal server plus PostgreSQL, registers the generated
`BatchProcessing` worker, and runs the workflow end-to-end. Other example-driven ITs in the same
package: `HttpResilienceDslIntegrationTest`, `UnreliableApiDslIntegrationTest`,
`DslVersioningIntegrationTest`, `PreviewDryRunIntegrationTest`, plus
`cbs.nova.starter.DslExamplesEndToEndTest`.

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:integrationTest \
  --tests cbs.nova.dsl.example.integration.BatchProcessingDslIntegrationTest
```

The test does the following:

1. Starts `postgres:15` and `temporalio/auto-setup:1.25.2` containers on a shared Docker
   network.
2. Loads `backend/dsl-starter/dsl-examples/src/dsl/` with `DefinitionLoader` into a fresh `GlobalManager`.
3. Points a Temporal `WorkflowClient` at the exposed gRPC port.
4. Registers `BatchProcessingProcessDefinition` on the `BatchProcessing-queue` task queue.
5. Executes the workflow with a `BatchIn` record and asserts the returned `BatchOut`.

## How input/output types are handled

When a process declares `.input(BatchIn.class)` and `.output(BatchOut.class)`, the DSL
generator produces a strongly-typed Temporal workflow interface:

```java
@WorkflowInterface
public interface BatchProcessingProcessWorkflow {
  @WorkflowMethod
  BatchOut run(BatchIn input);
}
```

Using concrete types lets Temporal serialize/deserialize the arguments and results correctly.
Without them, Temporal would deserialize JSON payloads as `LinkedHashMap` and the DSL body
would fail with a `ClassCastException`.

## Adding a new example

1. Create a compact source file in `backend/dsl-starter/dsl-examples/src/dsl/` (plus a companion
   `*Models.java` under `backend/dsl-starter/dsl-examples/src/models/` if the new process/transaction
   needs typed records).
2. Use `Dsl.process(...)`, `Dsl.transaction(...)`, or `Dsl.function(...)` inside `define()`.
3. Declare `.input(...)` / `.output(...)` when the workflow needs typed payloads.
4. Run `backend/dsl-platform/gradlew -p backend/dsl-starter :dsl-examples:compileDsl` to validate generation.
5. Optionally add an integration test under
   `backend/dsl-starter/starter/src/integrationTest/java/cbs/nova/dsl/example/integration/` that loads
   the new DSL, starts a Temporal worker, and executes the generated workflow.

## Tips

- DSL source files must not contain a `package` declaration or `public` modifier. They rely on
  the JEP-512 compact-source convention (`void main() {}` is required by the current loader).
- If `DefinitionLoader` reports compilation errors, fix the DSL source first; generated code
  will not be produced for files that fail to compile.
- The integration test resets `GlobalManager` before each run so tests do not share state
  between executions.

The Workbench 'New definition' dialog ships starter templates (plain process, saga, http pipeline, retry policy).

## Generating idempotency keys with `uuidV7`

The `uuidV7` helper produces an RFC 9562 version-7 UUID: a timestamp-prefixed,
lexicographically-sortable 128-bit value. Use it to build stable idempotency keys
for HTTP calls or correlation IDs for cross-system tracing.

```java
UuidV7Out key = ctx.runHelper("uuidV7", new UuidV7In(null)).as(UuidV7Out.class);

HttpCallOut response = ctx.runHelper("httpCall",
        new HttpCallIn(
                "https://api.example.com/payments",
                "POST",
                Map.of(
                        "Idempotency-Key", key.uuid(),
                        "Content-Type", "application/json"),
                jsonBody,
                null,
                null))
        .as(HttpCallOut.class);
```

The optional `namespace` argument makes the random tail deterministic per
namespace (derived from `SHA-256(namespace)`) while the timestamp and embedded
monotonic counter keep every generated value strictly ordered:

```java
UuidV7Out key = ctx.runHelper("uuidV7", new UuidV7In("payments/v1"))
        .as(UuidV7Out.class);
```

## Encoding values with `base64`

The `base64` helper encodes and decodes strings using standard or URL-safe Base64.
Set `mode` to `"encode"` or `"decode"` and optionally set `urlSafe` to `true` to use
the URL-safe alphabet (`-_` instead of `+/`). Padding is retained for both encoders.

Encode a JWT header URL-safe, as you would when building a manually signed JWT:

```java
Base64Out header = ctx.runHelper("base64",
        new Base64In("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", "encode", true))
        .as(Base64Out.class);
```

## Date formatting with `formatDate` and `parseDate`

The `formatDate` helper converts an ISO-8601 string or epoch-millis value into a formatted date/time
string. The `parseDate` helper does the reverse: it parses a formatted string back into an ISO-8601
instant. Both accept preset aliases such as `ISO_INSTANT`, `ISO_OFFSET_DATE_TIME`,
`ISO_ZONED_DATE_TIME`, and `RFC_1123_DATE_TIME`, or any raw
`java.time.format.DateTimeFormatter` pattern. An optional `zone` argument defaults to `UTC`.

Round-trip a timestamp through a custom pattern:

```java
FormatDateOut formatted = ctx.runHelper("formatDate",
        new FormatDateIn("2026-03-15T12:00:00Z", "yyyy-MM-dd HH:mm:ss", "UTC"))
        .as(FormatDateOut.class);

ParseDateOut parsed = ctx.runHelper("parseDate",
        new ParseDateIn(formatted.formatted(), "yyyy-MM-dd HH:mm:ss", "UTC"))
        .as(ParseDateOut.class);
```

Use `RFC_1123_DATE_TIME` to build HTTP header values such as `If-Modified-Since`:

```java
FormatDateOut ifModifiedSince = ctx.runHelper("formatDate",
        new FormatDateIn(String.valueOf(epochMillis), "RFC_1123_DATE_TIME", "UTC"))
        .as(FormatDateOut.class);

HttpCallOut response = ctx.runHelper("httpCall",
        new HttpCallIn(
                "https://api.example.com/resource",
                "GET",
                Map.of(
                        "If-Modified-Since", ifModifiedSince.formatted(),
                        "Accept", "application/json"),
                null,
                null,
                null))
        .as(HttpCallOut.class);
```

## Matching and extracting with `regex`

The `regex` helper performs case-insensitive `match`, `extract`, `replace`, and `split`
operations using Java regular expressions. Patterns are compiled once and cached in a bounded
LRU cache.

Extract a log level from a line, returning the first capturing group:

```java
String line = "2026-09-01 ERROR [worker-3] boom";
RegexOut level = ctx.runHelper("regex",
        new RegexIn("extract", "\\b(ERROR|WARN|INFO)\\b", line, null, 1, null))
        .as(RegexOut.class);
```

Validate that an identifier matches an expected format (matches anywhere in the input using
`Matcher.find()`, not a whole-string match):

```java
RegexOut valid = ctx.runHelper("regex",
        new RegexIn("match", "^[A-Z]{3}-\\d{4,8}$", requestId, null, null, null))
        .as(RegexOut.class);
```

## Signing and verifying with `hmacSha256Sign` and `hmacSha256Verify`

The `hmacSha256Sign` helper computes an HMAC-SHA256 signature for a message using the provided
secret. The `hmacSha256Verify` helper recomputes the expected signature and compares it to the
provided one with a constant-time byte comparison. Both helpers accept `hex` (default), `base64`,
and `base64url` encodings (case-insensitive).

Sign a webhook body to build an outbound `X-Cbs-Signature` header:

```java
HmacSha256SignOut signature = ctx.runHelper("hmacSha256Sign",
        new HmacSha256SignIn(body, secret, "hex"))
        .as(HmacSha256SignOut.class);

HttpCallOut response = ctx.runHelper("httpCall",
        new HttpCallIn(
                "https://api.partner.example.com/events",
                "POST",
                Map.of(
                        "Content-Type", "application/json",
                        "X-Cbs-Signature", "sha256=" + signature.signature()),
                body,
                null,
                null))
        .as(HttpCallOut.class);
```

Verify an inbound partner signature before processing the request:

```java
String header = requestHeaders.get("X-Partner-Signature");
String provided = header != null && header.startsWith("sha256=") ? header.substring(7) : null;

HmacSha256VerifyOut verified = ctx.runHelper("hmacSha256Verify",
        new HmacSha256VerifyIn(body, sharedSecret, provided, "hex"))
        .as(HmacSha256VerifyOut.class);

if (!verified.valid()) {
    // reject the request
}
```

## Building OAuth 2.0 PKCE authorize URLs with `urlEncode`

The `urlEncode` helper percent-encodes values for URL query parameters using RFC 3986 semantics
(space becomes `%20`, literal `+` is preserved). Set `form` to `true` to encode spaces as `+` for
`application/x-www-form-urlencoded` payloads instead.

Build an OAuth 2.0 PKCE authorize URL by encoding `state`, `code_challenge`, and `redirect_uri`
before concatenating them into the query string:

```java
UrlEncodeOut encodedState = ctx.runHelper("urlEncode",
        new UrlEncodeIn(state, null, false))
        .as(UrlEncodeOut.class);

UrlEncodeOut encodedChallenge = ctx.runHelper("urlEncode",
        new UrlEncodeIn(codeChallenge, null, false))
        .as(UrlEncodeOut.class);

UrlEncodeOut encodedRedirectUri = ctx.runHelper("urlEncode",
        new UrlEncodeIn(redirectUri, null, false))
        .as(UrlEncodeOut.class);

String authorizeUrl = "https://auth.example.com/authorize"
        + "?response_type=code"
        + "&client_id=" + clientId
        + "&redirect_uri=" + encodedRedirectUri.result()
        + "&state=" + encodedState.result()
        + "&code_challenge=" + encodedChallenge.result()
        + "&code_challenge_method=S256";
```

The `urlDecode` helper reverses the operation. Decode a query value before validation or storage:

```java
UrlDecodeOut decodedState = ctx.runHelper("urlDecode",
        new UrlDecodeIn(encodedState, null, false))
        .as(UrlDecodeOut.class);
```

## Content hashing with `sha256`

The `sha256` helper computes a plain SHA-256 digest of a string and encodes the result as
`hex` (default), `base64`, or `base64url`. A null input is rejected; an empty string is valid
and returns the well-known empty-string hash.

Generate an `ETag` from an HTTP response body and use it for cache-friendly polling with
`If-None-Match`. Skip re-processing when the server replies `304 Not Modified`:

```java
String responseBody = firstResponse.body(); // or previous stored body
Sha256Out etag = ctx.runHelper("sha256",
        new Sha256In(responseBody, "hex"))
        .as(Sha256Out.class);

HttpCallOut polled = ctx.runHelper("httpCall",
        new HttpCallIn(
                "https://api.example.com/resource",
                "GET",
                Map.of(
                        "If-None-Match", "\"" + etag.result() + "\"",
                        "Accept", "application/json"),
                null,
                null,
                null))
        .as(HttpCallOut.class);

if (polled.statusCode() == 304) {
    // no change since last poll — skip downstream processing
}
```

## Calling an authenticated API with `jwt` verify, `httpAuth` bearer, and `httpCall`

Verify a caller's JWT, build an `Authorization: Bearer ...` header, and forward the request to a downstream HTTP API. This wires trust, auth-header construction, and transport together in one process.

See `backend/dsl-starter/dsl-examples/src/dsl/JwtHttpCallDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/JwtHttpCallModels.java`).

```java
var verified = ctx.runHelper("jwt",
        new JwtIn("verify", in.token(), in.secret(), "HS256", null, null, null));
JwtOut jwtOut = verified.as(JwtOut.class);
@SuppressWarnings("unchecked")
Map<String, Object> claims = (Map<String, Object>)
        ((Map<String, Object>) jwtOut.result()).get("payload");
String subject = claims != null ? String.valueOf(claims.get("sub")) : null;

var auth = ctx.runHelper("httpAuth",
        new HttpAuthIn("bearer", in.token(), null, null, null, null, null, null));
HttpAuthOut authOut = auth.as(HttpAuthOut.class);

var call = ctx.runHelper("httpCall",
        new HttpCallIn(in.url(), method, authOut.headers(), in.body(), null, null));
HttpCallOut response = call.as(HttpCallOut.class);
```

With a valid HS256 token whose payload contains `"sub": "user-42"`, the process returns `verified=true`, `subject=user-42`, plus the HTTP response status and body from the downstream call.

## Applying a JSON Merge Patch and reading back with `jsonPatch` + `jsonExtract`

Use `jsonPatch` in `apply` mode to mutate a stored JSON document, then read a value out of the patched document with `jsonExtract` for a lightweight update-and-read round trip.

See `backend/dsl-starter/dsl-examples/src/dsl/JsonPatchRoundTripDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/JsonPatchModels.java`).

```java
var patched = ctx.runHelper("jsonPatch",
        new JsonPatchIn(in.sourceJson(), in.patchJson(), null, "apply"));
JsonPatchOut patchedOut = patched.as(JsonPatchOut.class);

var read = ctx.runHelper("jsonExtract",
        new JsonExtractIn(patchedOut.result(), in.readPath()));
JsonExtractOut readOut = read.as(JsonExtractOut.class);
```

Patching `{"status":"pending","counter":1}` with `{"status":"ready","counter":2}` and reading `status` returns `patchedJson={"status":"ready","counter":2}` and `extractedValue=ready` with `present=true`.

## Aggregating records with `listOps` pluck/groupBy and `arithmetic` mean/max

Turn a list of records into derived values: pull a numeric column with `listOps` `pluck`, group the same records with `groupBy`, then run `arithmetic` `mean` and `max` over the extracted numbers.

See `backend/dsl-starter/dsl-examples/src/dsl/RecordAggregationDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/AggregationModels.java`).

```java
var plucked = ctx.runHelper("listOps",
        new ListOpsIn("pluck", in.records(), null, null, in.valueField(), null));
List<Object> prices = (List<Object>) plucked.as(ListOpsOut.class).result();

var grouped = ctx.runHelper("listOps",
        new ListOpsIn("groupBy", in.records(), null, null, in.groupField(), null));
@SuppressWarnings("unchecked")
Map<String, List<Map<String, Object>>> groups =
        (Map<String, List<Map<String, Object>>>) grouped.as(ListOpsOut.class).result();

double mean = ((Number) ctx.runHelper("arithmetic",
        new ArithmeticIn("mean", null, (List<Number>) (List<?>) prices, null, null, null, null, null, null))
        .as(ArithmeticOut.class).result()).doubleValue();

double max = ((Number) ctx.runHelper("arithmetic",
        new ArithmeticIn("max", null, (List<Number>) (List<?>) prices, null, null, null, null, null, null))
        .as(ArithmeticOut.class).result()).doubleValue();
```

Given sales records grouped by `region` and values in `amount`, the process returns the extracted `prices`, a `groups` map keyed by region, plus the `mean` and `max` amounts.
## Checking a release window with `semver` compare and `dateMath` add

Gate a rollout on a minimum semantic version and compute the planned rollout date by adding days to a release anchor with `dateMath`.

See `backend/dsl-starter/dsl-examples/src/dsl/ReleaseWindowDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/ReleaseWindowModels.java`).

```java
var cmp = ctx.runHelper("semver",
        new SemverIn("compare", null, in.currentVersion(), in.minimumVersion(),
                null, null, null, null, null, null, null));
int comparison = ((Number) cmp.as(SemverOut.class).result()).intValue();
boolean versionOk = comparison >= 0;

var shifted = ctx.runHelper("dateMath",
        new DateMathIn("add", in.releaseDate(), null, in.daysToAdd(), "days", null));
String rolloutDate = shifted.as(DateMathOut.class).value();
```

Comparing current version `2.5.1` against minimum `2.4.0` yields `versionOk=true`. Adding `7` days to `2026-09-07` returns `rolloutDate=2026-09-14`.

## Gating a release with `semver` parse, satisfies, bump, and format

Parse an incoming artifact version, reject it when it falls outside the accepted range, otherwise bump it and stamp a build-metadata release candidate in one release-gate flow.

See `backend/dsl-starter/dsl-examples/src/dsl/ReleasePolicyDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/ReleasePolicyModels.java`).

```java
var parsed = ctx.runHelper("semver",
        new SemverIn("parse", in.incomingVersion(), null, null, null, null, null, null,
            null, null, null));
Map<String, Object> components = (Map<String, Object>) parsed.as(SemverOut.class).result();

var check = ctx.runHelper("semver",
        new SemverIn("satisfies", in.incomingVersion(), null, null, in.requiredRange(), null,
            null, null, null, null, null));
boolean rangeSatisfied = (Boolean) check.as(SemverOut.class).result();

var bumped = ctx.runHelper("semver",
        new SemverIn("bump", in.incomingVersion(), null, null, null, in.bumpType(), null,
            null, null, null, null));
String bumpedVersion = (String) bumped.as(SemverOut.class).result();

var candidate = ctx.runHelper("semver",
        new SemverIn("format", null, null, null, null, null, major, minor, patch + 1, null,
            in.buildMetadata()));
String nextCandidate = (String) candidate.as(SemverOut.class).result();
```

With `incomingVersion` = `1.4.2`, `requiredRange` = `^1.2.0`, `bumpType` = `minor` and `buildMetadata` = `build.42`, the gate passes (`rangeSatisfied=true`) and produces `bumpedVersion=1.5.0` plus `nextCandidate=1.4.3+build.42`. An out-of-range version such as `2.0.0` is rejected with `accepted=false` and no candidate.

## Building a CSV export from `parseCsv` and `parseYaml` with `formatCsv`

Parse a CSV data file, enrich it from a YAML lookup table, and emit a new RFC 4180 CSV string with `formatCsv`.

See `backend/dsl-starter/dsl-examples/src/dsl/CsvYamlExportDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/CsvYamlExportModels.java`).

```java
var csv = ctx.runHelper("parseCsv",
        new ParseCsvIn(in.csvPayload(), in.csvOptions()));
ParseCsvOut csvOut = csv.as(ParseCsvOut.class);

var yaml = ctx.runHelper("parseYaml", new ParseYamlIn(in.yamlPayload()));
@SuppressWarnings("unchecked")
Map<String, Object> lookup = (Map<String, Object>) yaml.as(ParseYamlOut.class).data();

List<List<String>> outRows = new ArrayList<>();
for (List<String> row : csvOut.rows()) {
  String code = row.get(0);
  String name = lookup.containsKey(code)
      ? String.valueOf(lookup.get(code))
      : code;
  outRows.add(List.of(code, name, row.size() > 1 ? row.get(1) : ""));
}

var formatted = ctx.runHelper("formatCsv",
        new FormatCsvIn(outRows, List.of("Code", "Name", "Source"), in.csvOptions()));
String csvResult = formatted.as(FormatCsvOut.class).csv();
```

With `csvOptions` set to `new CsvOptions(",", true, "\r\n")` (so `parseCsv` drops the header), feeding a CSV with header `Code,Source` and rows `US,web` plus a YAML map `US: United States` produces `csvResult` containing `Code,Name,Source\r\nUS,United States,web`.

## Constructing paginated URLs with `queryString` and `urlEncode`

Encode a raw path segment for RFC 3986 and build a form-encoded query string in one helper pipeline, then concatenate a clean paginated URL.

See `backend/dsl-starter/dsl-examples/src/dsl/PaginatedUrlDsl.java` (input/output models in `backend/dsl-starter/dsl-examples/src/models/PaginatedUrlModels.java`).

```java
var encoded = ctx.runHelper("urlEncode",
        new UrlEncodeIn(in.pathSegment(), null, false));
String encodedPath = encoded.as(UrlEncodeOut.class).result();

var query = ctx.runHelper("queryString",
        new QueryStringIn("build", in.queryParams(), null));
String queryString = (String) query.as(QueryStringOut.class).result();

String url = in.baseUrl() + "/" + encodedPath + "?" + queryString;
```

Encoding the segment `hello world` gives `hello%20world`, and building params `page=1` and `limit=50` gives `page=1&limit=50`. The result is a URL like `https://api.example.com/hello%20world?page=1&limit=50`.

## Log-safe card number in a notification message with `mask`

Mask a card number with the safe default (keep last 4, mask the rest) before interpolating it
into a customer-facing notification, so the full PAN never reaches log lines or the messaging
transport.

See `backend/dsl-starter/dsl-examples/src/dsl/MaskSensitiveDataDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/MaskSensitiveDataModels.java`).

```java
MaskOut maskedCard = ctx.runHelper("mask",
        new MaskIn(in.cardNumber(), null, null, null, null, null))
        .as(MaskOut.class);

var rendered = ctx.runHelper("formatMessage",
        new FormatMessageIn(in.notificationTemplate(), Map.of("card", maskedCard.result())));
String message = rendered.as(FormatMessageOut.class).result();
```

With `in.cardNumber()` = `4111111111111111` the safe default produces `************1111`.

## Redacting a field before writing an audit detail with `mask`

Redact a sensitive field with explicit edges and a custom mask char before placing it into an
audit-details map (e.g. one that is later serialized into `details_json`) — only the first and
last 2 code points survive.

See `backend/dsl-starter/dsl-examples/src/dsl/MaskSensitiveDataDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/MaskSensitiveDataModels.java`).

```java
MaskOut redacted = ctx.runHelper("mask",
        new MaskIn(in.payerReference(), "edges", 2, 2, "#", null))
        .as(MaskOut.class);

Map<String, Object> auditDetails = new LinkedHashMap<>();
auditDetails.put("payerReference", redacted.result());
auditDetails.put("amount", in.amount());
```

With `in.payerReference()` = `IBAN-DE8937` the result is `IB#######37`. If `keepFirst + keepLast`
reaches the value length, the keeps are clamped so at least one code point always stays masked —
the helper never returns the value unmasked. Use `mode="fixed"` (with an optional `width`,
default 8) when the output length must not depend on the input at all.

## Emitting Micrometer metrics with `metric` counter and timer

Emit a Micrometer counter and timer from within a process to demonstrate observability wiring.
The counter tracks total orders processed, tagged by category; the timer records the processing
duration in milliseconds with the same category tag.

See `backend/dsl-starter/dsl-examples/src/dsl/OrderMetricsDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/OrderMetricsModels.java`).

```java
var counter = ctx.runHelper("metric",
        new MetricIn("counter", "orders.processed", Map.of("category", in.productCategory()),
            null, 1L, null));
MetricOut counterOut = counter.as(MetricOut.class);

var timer = ctx.runHelper("metric",
        new MetricIn("timer", "orders.processing.duration", Map.of("category", in.productCategory()),
            null, null, processingTimeMs));
MetricOut timerOut = timer.as(MetricOut.class);
```

With `productCategory` = `"electronics"` and a 5ms processing time, the counter increments
`orders.processed` (tags: `category=electronics`) by 1 and records 5ms into the
`orders.processing.duration` timer. When no `MeterRegistry` bean is present, both calls validate
and return `emitted=false` as a no-op.

## Sharing pure pricing logic across a Process and a Transaction with `Function`

A `Function` is a DSL-declared helper that generates no Temporal code — it is registered in the
shared helper registry and runs locally. Use it to extract multi-step pure computation that more
than one Process/Transaction needs, without Activity overhead. A Function may call other Functions
and Helpers, but not Processes or Transactions.

See `backend/dsl-starter/dsl-examples/src/dsl/PricingFunctionsDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/PricingModels.java`).

The example declares two Functions and two consumers:

- `lineTotalFn` — multiplies each order line's `quantity * unitPrice` and sums the results with
  the built-in `arithmetic` helper (backward-compatible alias `math`) (Function → Helper).
- `orderPricingFn` — chains `lineTotalFn` (Function → Function), applies a 10% discount for
  `VIP` tier customers, adds 20% tax, and rounds each amount to two decimals with the `arithmetic`
  helper's `round` mode.
- `CheckoutProcess` (Process) and `QuoteTransaction` (Transaction) — both price an order through
  the same `orderPricingFn`, demonstrating "extract shared pure logic once".

Because Functions are dispatched through `GlobalManager.runFunction` and typed helper input
requires a typed context body, the example routes calls through small class-level dispatch
helpers (`callFunction` / `callHelper`) instead of the Map-based `ctx.runHelper` overloads.
A preview-mode test pinning the exact numbers lives in
`backend/dsl-starter/starter/src/test/java/cbs/nova/starter/PricingFunctionsDslTest.java`.

## Generating time-sortable order IDs with `uuidV7`

Mint three IDs for a logical order in one process: one with the default random tail, then two
with the same `namespace`. Because the namespace mixes deterministically into the 62-bit random
tail via `SHA-256(namespace)` while the timestamp and monotonic counter keep changing, both
namespaced IDs share the last 12 hex characters (the node group) but still sort and remain
unique. The process exposes that property through a boolean so a test can pin it.

See `backend/dsl-starter/dsl-examples/src/dsl/OrderIdGenerationDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/OrderIdGenerationModels.java`).

```java
var random = ctx.runHelper("uuidV7", new UuidV7In(null));
String randomId = random.as(UuidV7Out.class).uuid();

var namespaced1 = ctx.runHelper("uuidV7", new UuidV7In(in.namespace()));
String id1 = namespaced1.as(UuidV7Out.class).uuid();

var namespaced2 = ctx.runHelper("uuidV7", new UuidV7In(in.namespace()));
String id2 = namespaced2.as(UuidV7Out.class).uuid();

String tail1 = id1.substring(id1.lastIndexOf('-') + 1);
String tail2 = id2.substring(id2.lastIndexOf('-') + 1);
boolean deterministicTailMatch = tail1.equals(tail2);
```

With `orderId` = `"order-42"` and `namespace` = `"orders/v1"` the process returns three
distinct non-blank UUIDs, both namespaced values share the same node group, and the process
result records `deterministicTailMatch=true`.

## Computing capped-exponential retry delays with `backoff`

The `backoff` helper computes a retry delay for an attempt number given `baseMillis`,
`maxMillis` and a `jitter` strategy (`none`, `full`, `equal`, `decorrelated`). It performs no
Temporal-level retry itself — it is a pure delay computation you can reuse inside your own
retry loop. `none` returns the exact capped-exponential value `min(baseMillis * 2^attempt,
maxMillis)`; the randomized modes return a value within the same `[0, cap]` band.

See `backend/dsl-starter/dsl-examples/src/dsl/RetryPolicyDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/RetryPolicyModels.java`). The process walks a
simulated retry loop over attempts `0..maxAttempts-1` with `jitter` = `"none"` to expose the
deterministic schedule, then takes a single `"full"` jitter draw for the next attempt so both
modes are visible in one result:

```java
List<Long> noneDelays = new ArrayList<>();
for (int attempt = 0; attempt < in.maxAttempts(); attempt++) {
  var r = ctx.runHelper("backoff",
      new BackoffIn(attempt, in.baseMillis(), in.maxMillis(), "none", null));
  if (!r.isSuccess()) {
    return Result.failure(r.cause());
  }
  noneDelays.add(r.as(BackoffOut.class).delayMillis());
}

var full = ctx.runHelper("backoff",
    new BackoffIn(in.maxAttempts(), in.baseMillis(), in.maxMillis(), "full", null));
if (!full.isSuccess()) {
  return Result.failure(full.cause());
}
```

With `baseMillis` = `1000`, `maxMillis` = `60000` and `maxAttempts` = `6` the process records
`noneDelays` = `[1000, 2000, 4000, 8000, 16000, 32000]` while `fullDelay` is a fresh random
value guaranteed to lie within `[0, 60000]`.

## Generating synthetic order records with `random`

Mint a synthetic order record in one process: an alphanumeric `orderId` suffix (string mode),
a `customerId` in `[10000, 99999]` (int mode), an `amount` in `[10.0, 1000.0)` (double mode),
a `priority` chosen from `{low, medium, high}` and a `region` chosen from `{EU, US, APAC}`
(both via choice mode), plus three hex tags. The `random` helper is non-cryptographic — it is
backed by `ThreadLocalRandom` and is intended for sample data, ids, and load-test jitter only.
Do not use it for secrets, tokens, or any security-sensitive value.

See `backend/dsl-starter/dsl-examples/src/dsl/SampleDataGenerationDsl.java` (input/output models
in `backend/dsl-starter/dsl-examples/src/models/SampleDataGenerationModels.java`):

```java
var orderIdVar = ctx.runHelper("random",
    new RandomIn("string", null, null, null, null, null, null, 8, "alphanumeric", null));
String orderId = prefix + "-" + ((String) orderIdVar.as(RandomOut.class).result());

var custVar = ctx.runHelper("random",
    new RandomIn("int", 10000, 99999, null, null, null, null, null, null, null));
int customerId = (Integer) custVar.as(RandomOut.class).result();

var amountVar = ctx.runHelper("random",
    new RandomIn("double", null, null, null, null, 10.0, 1000.0, null, null, null));
double amount = (Double) amountVar.as(RandomOut.class).result();

var prioVar = ctx.runHelper("random",
    new RandomIn("choice", null, null, null, null, null, null, null, null,
        List.<Object>of("low", "medium", "high")));
String priority = (String) prioVar.as(RandomOut.class).result();
```

With `prefix` = `"ORD"` the process returns an `orderId` shaped `ORD-XXXXXXXX` (8 alphanumeric
chars), a `customerId` in `[10000, 99999]`, an `amount` in `[10.0, 1000.0)`, a `priority` from
the supplied pool, a `region` from `{EU, US, APAC}`, and three 6-character hex tags. All
ranges and pool membership are asserted — values are random by design.

### ApiKeyProvisioning — cryptographic API key / idempotency key via `secret` helper

This is the security-sensitive counterpart to the `random` helper example above. While `random`
is backed by `ThreadLocalRandom` and must never be used for secrets, the `secret` helper is backed
by `SecureRandom` and is intended for exactly these cases: API keys, tokens, signing secrets, and
idempotency keys.

The process demonstrates both `secret` modes:
- **`token`** — a URL-safe opaque string of `length` characters drawn uniformly from the 64-char
  Base64url alphabet (`A-Z a-z 0-9 - _`), yielding 6 bits of entropy per character. A `length` of
  32 produces a 192-bit API key — the same entropy the helper's own javadoc recommends for session
  tokens and API keys.
- **`bytes`** — `length` random bytes returned as a hex-encoded string (default encoding). 16 bytes
  → 128-bit hex string — a common size for idempotency keys.

See `backend/dsl-starter/dsl-examples/src/dsl/ApiKeyProvisioningDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/ApiKeyProvisioningModels.java`):

```java
// token mode — 32-char URL-safe API key (192 bits entropy).
var apiKeyVar = ctx.runHelper("secret",
    new SecretIn("token", 32, null));
String apiKey = apiKeyVar.as(SecretOut.class).result();

// bytes mode — 16-byte idempotency key, hex-encoded (128 bits).
var idempotencyVar = ctx.runHelper("secret",
    new SecretIn("bytes", 16, "hex"));
String idempotencyKey = idempotencyVar.as(SecretOut.class).result();
```

With `purpose` = `"payment-service"` the process returns the echoed purpose, a 32-character
Base64url API key (`[A-Za-z0-9_-]{32}`), and a 32-character hex idempotency key (`[0-9a-f]{32}`).
Length and charset are asserted — values are cryptographic by design.

## Resolving a schedule window with `parseDuration` (ISO-8601 + shorthand)

Resolve a human-entered schedule window into a normalized form by running each duration string
through the `parseDuration` helper. The helper accepts both shapes:
- **ISO-8601** strings parseable by `java.time.Duration.parse(...)`, e.g. `"PT1H30M"`, `"P2DT3H"`,
  `"PT0.5S"`. Bare-day forms such as `"P2D"` normalize to a time component (`"PT48H"`).
- **Shorthand** made of one or more `<number><unit>` segments, e.g. `"90m"`, `"1h30m"`,
  `"2d12h"`, `"250ms"`. Units are `d`, `h`, `m` (always minutes, never months), `s`, and `ms`.

For every accepted input the helper returns `millis` (total milliseconds), `seconds`
(`Duration.toSeconds()`), and `iso` (the normalized ISO-8601 string). The process echoes the
`jobName` and both parsed windows so a downstream scheduler can store a single canonical form
regardless of which shape the operator typed.

See `backend/dsl-starter/dsl-examples/src/dsl/ScheduleWindowDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/ScheduleWindowModels.java`):

```java
// ISO-8601 form — e.g. "PT1H30M" or "P2DT3H".
var graceVar = ctx.runHelper("parseDuration",
    new ParseDurationIn(in.gracePeriod()));
if (!graceVar.isSuccess()) {
  return Result.failure(graceVar.cause());
}
ParseDurationOut graceOut = graceVar.as(ParseDurationOut.class);

// Shorthand form — e.g. "1h30m", "2d12h", "250ms".
var hardVar = ctx.runHelper("parseDuration",
    new ParseDurationIn(in.hardLimit()));
if (!hardVar.isSuccess()) {
  return Result.failure(hardVar.cause());
}
ParseDurationOut hardOut = hardVar.as(ParseDurationOut.class);

return Result.success(new ScheduleWindowOut(
    in.jobName(),
    graceOut.millis(), graceOut.seconds(), graceOut.iso(),
    hardOut.millis(), hardOut.seconds(), hardOut.iso()));
```

With `jobName` = `"nightly-rollout"`, `gracePeriod` = `"PT1H30M"` (ISO-8601) and `hardLimit`
= `"2h"` (shorthand) the process returns `graceMillis=5_400_000`, `graceSeconds=5400`,
`graceIso="PT1H30M"`, `hardLimitMillis=7_200_000`, `hardLimitSeconds=7200`,
`hardLimitIso="PT2H"`. The second preview test (`jobName="batch-flush"`,
`gracePeriod="45m"`, `hardLimit="P2DT3H"`) flips the assignment so both forms are exercised
in both slots — `45m` yields `graceMillis=2_700_000` / `graceSeconds=2700` / `graceIso="PT45M"`,
and `P2DT3H` (2 days + 3 hours = 51 hours) yields `hardLimitMillis=183_600_000` /
`hardLimitSeconds=183_600` / `hardLimitIso="PT51H"` — bare-day forms normalize to a time
component. Values are asserted exactly because both inputs are deterministic.
## Stamping an audit trail with `currentTimestamp` (UTC + explicit zone)

Stamp an audit/event record with a Temporal-workflow-safe timestamp by running the
`currentTimestamp` helper. The helper returns a single ISO-8601 offset timestamp string and
accepts an optional `zone` argument:
- **No zone** (or `null`/blank) — defaults to `UTC`, rendered with a trailing `Z`
  (e.g. `2026-09-21T06:30:00Z`).
- **Explicit zone** — any IANA timezone name such as `"Asia/Kolkata"`, rendered as an offset
  string (e.g. `2026-09-21T11:30:00+05:30`). Invalid zones fall back to UTC.

The helper is Temporal-replay-safe: it derives the instant from
`Workflow.currentTimeMillis()` when running inside a Temporal workflow so the value is
deterministic under replay, only falling back to `Instant.now()` outside a workflow context
(such as a preview run). The process stamps a single event twice — once canonical UTC, once in
the caller's zone — so a downstream audit sink can store a stable UTC value while retaining a
human-readable local offset.

See `backend/dsl-starter/dsl-examples/src/dsl/AuditTrailDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/AuditTrailModels.java`):

```java
// Default UTC form — no zone argument.
var utcVar = ctx.runHelper("currentTimestamp",
    new CurrentTimestampIn(null));
if (!utcVar.isSuccess()) {
  return Result.failure(utcVar.cause());
}
String utcTimestamp = utcVar.as(CurrentTimestampOut.class).timestamp();

// Explicit caller-specified timezone — e.g. "Asia/Kolkata".
var localVar = ctx.runHelper("currentTimestamp",
    new CurrentTimestampIn(in.zone()));
if (!localVar.isSuccess()) {
  return Result.failure(localVar.cause());
}
String localTimestamp = localVar.as(CurrentTimestampOut.class).timestamp();

return Result.success(new AuditTrailOut(
    in.eventType(), in.actor(), utcTimestamp, localTimestamp, in.zone()));
```

With `eventType` = `"ORDER_STATUS_CHANGED"`, `actor` = `"scheduler"` and
`zone` = `"Asia/Kolkata"` the process returns the echoed event metadata, a UTC timestamp
matching `\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z`, and a local timestamp matching
`\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+\d{2}:\d{2}` with a `+05:30` offset. The test asserts
the format and offset shape rather than exact clock values, since the clock is not
deterministic across runs.

## Signing and verifying a webhook payload with `hmacSha256Sign` + `hmacSha256Verify`

Demonstrate the outbound/inbound HMAC-SHA256 webhook signature handshake in one process.
The helper computes an HMAC-SHA256 of the payload with a shared secret to build the
`X-Signature: sha256=<digest>` header value, then verifies three scenarios on the inbound
side — same payload + same secret (accept), tampered payload + original signature (reject),
and original payload + wrong secret (reject). The accepted scenario proves the end-to-end
sign+verify round-trip; the two rejected scenarios pin the constant-time comparison
behaviour for the two common webhook-tampering failure modes.

See `backend/dsl-starter/dsl-examples/src/dsl/WebhookSignatureDsl.java` (input/output models
in `backend/dsl-starter/dsl-examples/src/models/WebhookSignatureModels.java`):

```java
// Outbound: sign the payload with the shared secret.
var signedVar = ctx.runHelper("hmacSha256Sign",
    new HmacSha256SignIn(in.payload(), in.secret(), in.encoding()));
if (!signedVar.isSuccess()) {
  return Result.failure(signedVar.cause());
}
HmacSha256SignOut signed = signedVar.as(HmacSha256SignOut.class);
String outboundSignature = signed.signature();
String outboundHeader = "sha256=" + outboundSignature;

// Inbound (a): same payload + same secret — accept.
var okVar = ctx.runHelper("hmacSha256Verify",
    new HmacSha256VerifyIn(in.payload(), in.secret(), outboundSignature, in.encoding()));
boolean validSignedPayload = okVar.as(HmacSha256VerifyOut.class).valid();

// Inbound (b): tampered payload + original signature — reject.
var tamperVar = ctx.runHelper("hmacSha256Verify",
    new HmacSha256VerifyIn(in.tamperedPayload(), in.secret(), outboundSignature, in.encoding()));
boolean validTamperedPayload = tamperVar.as(HmacSha256VerifyOut.class).valid();

// Inbound (c): original payload + wrong secret — reject.
var wrongVar = ctx.runHelper("hmacSha256Verify",
    new HmacSha256VerifyIn(in.payload(), in.wrongSecret(), outboundSignature, in.encoding()));
boolean validWrongSecret = wrongVar.as(HmacSha256VerifyOut.class).valid();
```

With `payload` = `{"event":"order.created","id":"order-42"}`, `secret` = the shared
signing secret, `wrongSecret` = a different secret, `tamperedPayload` =
`{"event":"order.created","id":"order-99"}` and `encoding` = `"hex"`, the process returns
`outboundSignature` matching `[0-9a-f]{64}`, `outboundHeader` =
`"sha256=" + outboundSignature`, `encoding` = `"hex"`, `validSignedPayload` = `true`,
`validTamperedPayload` = `false`, and `validWrongSecret` = `false`. The shared secret is
never echoed in the process output — only the derived signature header and the boolean
verification results leave the workflow.

## Minimal process shape with `SampleProcessDsl`

The smallest possible example: no helpers, no typed models, `String` in and `String` out. Use it
as the reference for what a compact DSL source must contain (a `define()` returning
`List<DslObject>`, no `package`/`public`).

See `backend/dsl-starter/dsl-examples/src/dsl/SampleProcessDsl.java`:

```java
List<DslObject> define() {
  return Dsl.process("SampleProcess")
      .input(String.class)
      .output(String.class)
      .execute(ctx -> Result.success("Hello from DSL: " + ctx.body()))
      .buildList();
}
```

## Parameter-only process and `MapOutput` with `SimpleOrderDsl`

Shows the parameters-only input shape (no `.input(...)` type — the body is a
`MapOutput` parameter bag) plus `ctx.runId()` for log correlation and a
compensation block.

See `backend/dsl-starter/dsl-examples/src/dsl/SimpleOrderDsl.java`:

```java
Dsl.process("SimpleOrder")
    .parameters(reg -> reg.string("orderId"))
    .execute(ctx -> {
      var params = ctx.body();
      return Result.success(MapOutput.of(
          "orderId", params.values().get("orderId"),
          "message", "Order " + params.values().get("orderId")
              + " confirmed (runId=" + ctx.runId() + ")"));
    })
    .compensation((ctx, history) ->
        ctx.log("compensating SimpleOrder: " + ctx.error().getMessage()))
    .buildList();
```

## Awaiting a signal with `SignalProbeDsl`

Demonstrates `.signal("approval", Map.class)` declaration plus `ctx.awaitSignal(...)` inside the
execute body — the process blocks until an external signal named `approval` arrives, then reads
fields off the signal payload.

See `backend/dsl-starter/dsl-examples/src/dsl/SignalProbeDsl.java`:

```java
Dsl.process("SignalProbe")
    .input(String.class)
    .output(String.class)
    .signal("approval", Map.class)
    .execute(ctx -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> approval = ctx.awaitSignal("approval", Map.class);
      boolean approved = Boolean.TRUE.equals(approval.get("approved"));
      String reviewer = String.valueOf(approval.getOrDefault("reviewer", "unknown"));
      return Result.success("approved=" + approved + ":" + reviewer + ":" + ctx.body());
    })
    .buildList();
```

Covered end-to-end by `SignalProbeDslIntegrationTest`.

## Branching on a signal in preview with `SignalBranchDsl`

`SignalProbeDsl` waits for a real Temporal signal, so it can only be exercised against a worker.
`SignalBranchDsl` covers the same declare-then-branch shape for the **preview** path: the process
declares `.signal("decision", Map.class)`, peeks with `signalReceived` / `getSignalPayload`, then
awaits and branches on the seeded payload. Because preview resolves signals from the request's
seeded timeline (T736, see [preview-mode.md](preview-mode.md#preview-mode-signals)), no Temporal
worker is needed.

See `backend/dsl-starter/dsl-examples/src/dsl/SignalBranchDsl.java`:

```java
Dsl.process("SignalBranch")
    .input(SignalBranchIn.class)
    .output(SignalBranchOut.class)
    .signal("decision", Map.class)
    .execute(ctx -> {
      SignalBranchIn in = ctx.body();
      boolean seenBeforeAwait = ctx.signalReceived("decision");
      Object peek = ctx.getSignalPayload("decision", Map.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> decision =
          (Map<String, Object>) ctx.awaitSignal("decision", Map.class);
      boolean approved = Boolean.TRUE.equals(decision.get("approved"));
      return Result.success(new SignalBranchOut(
          (approved ? "APPROVED" : "REJECTED") + ":" + in.amount(),
          in.requestId(), seenBeforeAwait && peek != null));
    })
    .buildList();
```

Previewing it with a seeded timeline:

```json
{ "body": {"requestId": "req-1", "amount": 250},
  "signals": [{"name": "decision", "payloadJson": "{\"approved\":true}"}] }
```

Covered by `AdvancedDslExamplesTest.signalBranchPreviewResolvesSeededSignalAndBranchesOnPayload`
(and its reject / no-buffer siblings).

## Pinning a DSL version with `VersionProbeDsl`

Shows `.version("v1")`, which pins the definition so an in-flight workflow keeps running the
originally loaded DSL after the registry is reloaded with a newer definition. The `fileLatch`
helper bridges the reload boundary so a test can hold one execution open while swapping the
registry.

See `backend/dsl-starter/dsl-examples/src/dsl/VersionProbeDsl.java`:

```java
Dsl.process("VersionProbe")
    .input(VersionProbeIn.class)
    .output(VersionProbeOut.class)
    .version("v1")
    .execute(ctx -> {
      String lockFile = "lock-" + ctx.runId();
      String releaseFile = "release-" + ctx.runId();
      var latch = ctx.runHelper("fileLatch",
          new FileLatchIn(lockFile, releaseFile, in.payload()));
      ...
      return Result.success(new VersionProbeOut("v1:" + out.payload()));
    })
    .buildList();
```

Covered by `DslVersioningIntegrationTest`.

## Publishing to RabbitMQ with `AmqpPublishDsl`

Publishes a JSON payload to an exchange via the `amqpPublish` helper and returns the routing key
and the publish timestamp. Shows the `isSuccess()` / `cause()` / `as(...)` guard pattern around a
single helper call.

See `backend/dsl-starter/dsl-examples/src/dsl/AmqpPublishDsl.java`:

```java
var published = ctx.runHelper("amqpPublish",
    new AmqpPublishIn(in.exchange(), in.routingKey(), in.payload()));
if (!published.isSuccess()) {
  return Result.failure(published.cause());
}
AmqpPublishOut out = published.as(AmqpPublishOut.class);
return Result.success(new PublishOut(out.routingKey(), out.timestamp()));
```

## Publishing to Kafka with `KafkaPublishDsl`

Publishes a JSON payload to a topic via the `kafkaPublish` helper and returns the topic, partition,
offset, and timestamp. Same `isSuccess()` / `cause()` / `as(...)` guard pattern as `AmqpPublishDsl`.
Needs a `KafkaTemplate` bean (opt-in; see the `kafkaPublish` entry in [helpers](helpers.md)).

See `backend/dsl-starter/dsl-examples/src/dsl/KafkaPublishDsl.java`:

```java
var published = ctx.runHelper("kafkaPublish",
    new KafkaPublishIn(in.topic(), in.key(), in.payload()));
if (!published.isSuccess()) {
  return Result.failure(published.cause());
}
KafkaPublishOut out = published.as(KafkaPublishOut.class);
return Result.success(new PublishOut(out.topic(), out.partition(), out.offset(), out.timestamp()));
```

## Extracting XML by XPath with `XmlExtractDsl`

Uses `extractXml` twice against the same SOAP-ish envelope: one XPath that always matches and one
that is expected not to, so the `present` flag distinguishes "matched empty" from "no match".

See `backend/dsl-starter/dsl-examples/src/dsl/XmlExtractDsl.java`:

```java
var primary = ctx.runHelper("extractXml",
    new XmlExtractIn(xml, in.primaryXpath()));     // present = true
var optional = ctx.runHelper("extractXml",
    new XmlExtractIn(xml, in.optionalXpath()));   // present = false, value = null
```

## Filtering, sorting and projecting records with `RecordOpsDsl`

A three-step record pipeline: `filterRecords` narrows a mixed catalog by field/value, `sortRecords`
orders the survivors by a different numeric field, and `pick` projects each row down to the output
keys.

See `backend/dsl-starter/dsl-examples/src/dsl/RecordOpsDsl.java`:

```java
var filtered = ctx.runHelper("filterRecords",
    new FilterRecordsIn(records, "category", in.category()));
var sorted = ctx.runHelper("sortRecords",
    new SortRecordsIn(filtered.as(FilterRecordsOut.class).matched(),
        "amount", in.ascending(), null, null));
for (Map<String, Object> row : sorted.as(SortRecordsOut.class).records()) {
  projected.add(ctx.runHelper("pick", new PickIn(row, in.pickKeys(), "pick"))
      .as(PickOut.class).result());
}
```

## Plucking, de-duplicating and tallying with `ListOpsDsl`

Three `listOps` modes over an inline record set: `pluck` projects one field's values in record
order, `distinct` de-duplicates preserving first-seen order, and `countBy` tallies records per
field value.

See `backend/dsl-starter/dsl-examples/src/dsl/ListOpsDsl.java`:

```java
var plucked = ctx.runHelper("listOps",
    new ListOpsIn("pluck", records, null, null, in.categoryField(), null));
var distinct = ctx.runHelper("listOps",
    new ListOpsIn("distinct", null, categories, null, null, null));
var counted = ctx.runHelper("listOps",
    new ListOpsIn("countBy", records, null, null, in.countField(), null));
```

## Chaining helpers with JSON-path extraction in `HelperPipelineDsl`

Shows the record → aggregate → render chain: `filterRecords` selects matching rows, plain Java
streams pull the numeric field out, `arithmetic` sums the list, `formatMessage` renders a message
from the result, and `ctx.asJsonValue(...)` walks a dotted path through the input JSON (including
array indices) to extract a leaf value.

See `backend/dsl-starter/dsl-examples/src/dsl/HelperPipelineDsl.java`:

```java
var filtered = ctx.runHelper("filterRecords",
    new FilterRecordsIn(in.records(), in.filterField(), in.filterValue()));
List<Number> amounts = matched.stream().map(r -> (Number) r.get("amount")).toList();
var summed = ctx.runHelper("arithmetic",
    new ArithmeticIn(null, null, amounts, null, null, null, null, null, null));
var rendered = ctx.runHelper("formatMessage",
    new FormatMessageIn(in.messageTemplate(),
        Map.of("count", matched.size(), "total", summed.as(ArithmeticOut.class).sum())));

JsonValue extracted = ctx.asJsonValue(in.payloadJson());
for (String segment : in.extractPath().split("\\.")) { /* get(segment) or get(index) */ }
```

## Compressing, hashing and encoding with `DataIntegrityDsl`

A transport-integrity pipeline: `compression` (gzip) shrinks the payload, `sha256` hashes the
compressed bytes as hex, and `base64` encodes the same compressed payload for transport. The output
carries both the digest and the encoded payload so a receiver can verify before decoding.

See `backend/dsl-starter/dsl-examples/src/dsl/DataIntegrityDsl.java`:

```java
var compressedVar = ctx.runHelper("compression",
    new CompressionIn("gzip", in.payload(), null));
String compressed = compressedVar.as(CompressionOut.class).result();
String digest = ctx.runHelper("sha256", new Sha256In(compressed, "hex"))
    .as(Sha256Out.class).result();
String encodedPayload = ctx.runHelper("base64", new Base64In(compressed, "encode", false))
    .as(Base64Out.class).result();
```

## Reconciling a customer record with `similarity` + `jsonDiff`

Deduplicate a candidate record against an existing list of customer records via fuzzy
similarity, then surface field-level changes between the matched record's JSON payload and
the candidate's JSON payload. This is the canonical "inbound record came in again — find the
nearest existing match and tell me exactly what changed" flow.

The process scores each candidate against `candidateName` under the requested `mode`
(`jaroWinkler`, `levenshtein`, or `ratio`), keeps the highest-scoring candidate above
`matchThreshold`, then calls `jsonDiff` with the matched record's JSON on the left and the
candidate JSON on the right. The output exposes the chosen `mode`, the `bestScore` and
`bestCandidateName`, whether a candidate cleared the threshold (`matched` + `matchedIndex`),
plus the `jsonDiff` result (`identical`, `changeCount`, and the first per-path change so
callers can pivot on the leading diff entry).

See `backend/dsl-starter/dsl-examples/src/dsl/RecordReconcileDsl.java` (input/output models
in `backend/dsl-starter/dsl-examples/src/models/RecordReconcileModels.java`):

```java
double bestScore = 0.0;
int bestIndex = -1;
String bestName = "";
String nameField = in.candidateField() == null ? "name" : in.candidateField();
String mode = in.mode() == null ? "jaroWinkler" : in.mode();
for (int i = 0; i < in.candidates().size(); i++) {
  Map<String, Object> entry = in.candidates().get(i);
  Object raw = entry == null ? null : entry.get(nameField);
  String other = raw == null ? "" : String.valueOf(raw);
  var scored = ctx.runHelper("similarity",
      new SimilarityIn(mode, in.candidateName(), other, false));
  if (!scored.isSuccess()) {
    return Result.failure(scored.cause());
  }
  double score = scored.as(SimilarityOut.class).score();
  if (score > bestScore) {
    bestScore = score;
    bestIndex = i;
    bestName = other;
  }
}
boolean matched = bestIndex >= 0 && bestScore >= in.matchThreshold();
Map<String, Object> matchedRecord = matched ? in.candidates().get(bestIndex) : Map.of();
String matchedJson = serializeAsJson(matchedRecord);

var diff = ctx.runHelper("jsonDiff",
    new JsonDiffIn(matchedJson, in.candidateJson()));
JsonDiffOut diffOut = diff.as(JsonDiffOut.class);
```

With `candidateName` = `"Anne-Sophie Müller"`, candidates including
`{"name":"Anne Müller","email":"anne@example.com","tier":"gold"}`, and a candidate JSON of
`{"name":"Anne-Sophie Müller","email":"anne.sophie@example.com","tier":"platinum"}`, the
process returns `bestCandidateName="Anne Müller"`, `matched=true`, `matchedIndex=1`, a
non-trivial `bestScore`, plus a non-identical diff (`changeCount > 0`) with the leading
entry's `op` in `{changed, added, removed}`. The threshold sub-test uses a `0.999` threshold
against an unrelated candidate list so `matched=false` / `matchedIndex=-1` and the diff
falls back to the empty-object left side (every candidate JSON field is reported as
`added`). `mode` and `matchThreshold` echo the caller's values so callers can audit which
algorithm and cutoff produced the verdict.

## Composing a saga in `OrderSagaDsl`

The canonical two-step saga: two `Dsl.transaction(...)` definitions (`reserveInventory`,
`chargePayment`), each with its own single-argument compensation, invoked from a process via
`ctx.runTransaction(...)` with a success check after every step and a process-level compensation
that logs the failure.

See `backend/dsl-starter/dsl-examples/src/dsl/OrderSagaDsl.java`:

```java
var inventoryTx = Dsl.transaction("reserveInventory")
    .input(OrderSagaIn.class)
    .output(InventoryReservation.class)
    .startToCloseTimeout(Duration.ofSeconds(10))
    .execute(ctx -> Result.success(
        new InventoryReservation(in.orderId(), in.quantity())))
    .compensation(ctx -> {
      ctx.log("compensating reserveInventory");
      return Result.success("inventory released");
    })
    .build();
// ... chargePayment analogous ...
var inv = ctx.runTransaction("reserveInventory", in);
if (!inv.isSuccess()) {
  return Result.failure(inv.cause());
}
```

## Nested transaction compensation in `NestedCompensationDsl`

Three transactions (`ncStep1`, `ncStep2`, `ncStep3`) where only the third fails. Each transaction
declares its own compensation, so the failure in step 3 shows both levels rolling back: the failing
transaction's compensation and the process-level compensation.

See `backend/dsl-starter/dsl-examples/src/dsl/NestedCompensationDsl.java`:

```java
var step3 = Dsl.transaction("ncStep3")
    .input(NestedCompensationIn.class)
    .output(String.class)
    .startToCloseTimeout(Duration.ofSeconds(5))
    .execute(ctx -> Result.failure(new RuntimeException("step3 deliberately failed")))
    .compensation(ctx -> {
      ctx.log("ncStep3 compensated");
      return Result.success("ok");
    })
    .build();
// process: runTransaction ncStep1, ncStep2, then ncStep3 -> failure propagates
```

Note the single-argument `.compensation(ctx -> ...)` form used by transactions versus the
two-argument `.compensation((ctx, history) -> ...)` form used by processes.

## Validating JSON payloads with `SchemaValidationDsl`

Three `schemaValidate` calls over one schema: a valid payload, an invalid payload collecting every
error, and a fail-fast payload that stops at the first violation. The output exposes the error count
plus the first error's `field()` and `message()` for both failure modes.

See `backend/dsl-starter/dsl-examples/src/dsl/SchemaValidationDsl.java`:

```java
var valid = ctx.runHelper("schemaValidate",
    new SchemaValidateIn(in.validPayload(), in.schema(), null));
var invalid = ctx.runHelper("schemaValidate",
    new SchemaValidateIn(in.invalidPayload(), in.schema(), null));      // all errors
var failFast = ctx.runHelper("schemaValidate",
    new SchemaValidateIn(in.failFastPayload(), in.schema(), in.failFast())); // first error only
```

## Validating a YAML config blob with `ConfigValidationDsl`

Round-trips a config blob through `parseYaml` → `formatYaml` (canonical block-style YAML), schema-
checks it with `validateJson`, then applies field-level helpers: `regex` to assert the version is a
semver triple, `parseDate` to normalize an embedded release date to ISO-8601, and `formatNumber` to
render a numeric threshold. A schema violation fails the process with the collected errors.

See `backend/dsl-starter/dsl-examples/src/dsl/ConfigValidationDsl.java`:

```java
Map<String, Object> config = ctx.runHelper("parseYaml", new ParseYamlIn(in.configYaml()))
    .as(ParseYamlOut.class).data();
String canonicalYaml = ctx.runHelper("formatYaml", new FormatYamlIn(config))
    .as(FormatYamlOut.class).yaml();
String payload = io.avaje.jsonb.Jsonb.builder().build().type(Object.class).toJson(config);
var validatedOut = ctx.runHelper("validateJson",
    new ValidateJsonIn(payload, in.schema())).as(ValidateJsonOut.class);
if (!validatedOut.valid()) {
  return Result.failure(new IllegalStateException(
      "config failed schema validation: " + validatedOut.errors()));
}
var versionCheck = ctx.runHelper("regex",
    new RegexIn("match", "^\\d+\\.\\d+\\.\\d+$", String.valueOf(config.get("version")),
        null, null, null));
```

## Sanitizing inbound content with `ContentSanitizeDsl`

The inbound-content sanitization set: `urlDecode` the form-encoded fields, `htmlEscape` text bound
for HTML insertion, `xmlEscape` text bound for XML, `caseConvert` a title into a kebab slug,
`formatDate` a raw epoch-millis timestamp into an ISO-8601 UTC instant, and `groupRecords` the
sanitized items by a caller-chosen key.

See `backend/dsl-starter/dsl-examples/src/dsl/ContentSanitizeDsl.java`:

```java
String title = ctx.runHelper("urlDecode",
    new UrlDecodeIn(in.encodedTitle(), "UTF-8", true)).as(UrlDecodeOut.class).result();
String htmlSafeBody = ctx.runHelper("htmlEscape", new HtmlEscapeIn(body))
    .as(HtmlEscapeOut.class).result();
String xmlSafeMeta = ctx.runHelper("xmlEscape", new XmlEscapeIn(meta))
    .as(XmlEscapeOut.class).result();
String kebabSlug = ctx.runHelper("caseConvert", new CaseConvertIn(title, "kebab"))
    .as(CaseConvertOut.class).result();
String isoTimestamp = ctx.runHelper("formatDate",
    new FormatDateIn(Long.toString(in.rawTimestampMillis()), "ISO_INSTANT", "UTC"))
    .as(FormatDateOut.class).formatted();
Map<Object, List<Map<String, Object>>> groups = ctx.runHelper("groupRecords",
    new GroupRecordsIn(sanitized, in.groupByField())).as(GroupRecordsOut.class).groups();
```

## Safe placeholder substitution with `SafeTemplatingDsl`

Contrasts the two templating helpers. `interpolate` is the safe choice for anything that could come
from configuration or user input: it supports nested-path lookups (`${order.id}`), inline defaults
(`${order.priority:-standard}`), escaping (`$${order.total}` stays literal), and an `onMissing`
policy (`"empty"`) that keeps a missing key from failing the process. `formatMessage` evaluates
SpEL and is a code-execution surface — safe here only because the template is an operator-authored
constant baked into the DSL source.

See `backend/dsl-starter/dsl-examples/src/dsl/SafeTemplatingDsl.java`:

```java
Map<String, Object> params = Map.of("order", in.order());
var nested = ctx.runHelper("interpolate",
    new InterpolateIn("Order #${order.id} placed", params, null));
var withDefault = ctx.runHelper("interpolate",
    new InterpolateIn("Priority: ${order.priority:-standard}", params, null));
var escaped = ctx.runHelper("interpolate",
    new InterpolateIn("Raw token: $${order.total} (kept literal)", params, null));
var missing = ctx.runHelper("interpolate",
    new InterpolateIn("Coupon: ${order.couponCode}", params, "empty"));
var formatted = ctx.runHelper("formatMessage",
    new FormatMessageIn(in.greetingTemplate(), Map.of("orderId", in.order().get("id"))));
```

## Pre-flighting a Temporal schedule with `ScheduleGuardDsl`

The schedule guard: `cronParse` in `validate` mode first (fail fast on bad syntax), then
`cronParse` in `nextFire` mode against a caller-pinned `after` timestamp to preview the
upcoming fire times. Use as a gate before creating a Temporal schedule so misconfigured
expressions never reach the scheduler — operators see the verdict (`valid` + ordered
`fireTimes`) or a structured `errorMessage` without having to spin up the worker.

The helper accepts the same three shapes the starter's `cronParse` documents: the
DST-safe 6-field form (`sec min hour dom mon dow`), the 5-field Unix form
(`min hour dom mon dow`, the helper prepends "0 " seconds), and macro shorthands
(`@daily`, `@hourly`, `@yearly`, ...). All times are evaluated as UTC wall-clock
times; the returned `fireTimes` are Z-normalized ISO-8601 instants so callers can
diff/sort them without re-parsing.

See `backend/dsl-starter/dsl-examples/src/dsl/ScheduleGuardDsl.java` (input/output models in
`backend/dsl-starter/dsl-examples/src/models/ScheduleGuardModels.java`):

```java
var validate = ctx.runHelper("cronParse",
    new CronParseIn(in.expression(), "validate", null, null));
if (!validate.isSuccess()) {
  return Result.success(new ScheduleGuardOut(
      in.scheduleName(), in.expression(), false, null,
      validate.cause().getMessage()));
}
var preview = ctx.runHelper("cronParse",
    new CronParseIn(in.expression(), "nextFire", in.fixedAfter(), in.previewCount()));
if (!preview.isSuccess()) {
  return Result.success(new ScheduleGuardOut(
      in.scheduleName(), in.expression(), false, null,
      preview.cause().getMessage()));
}
List<String> fireTimes = preview.as(CronParseOut.class).timestamps();
return Result.success(new ScheduleGuardOut(
    in.scheduleName(), in.expression(), true, fireTimes, null));
```

With `expression = "0 0 12 * * *"` (6-field daily noon), `fixedAfter = "2024-01-15T00:00:00Z"`,
and `previewCount = 3`, the guard returns `fireTimes = ["2024-01-15T12:00:00Z",
"2024-01-16T12:00:00Z", "2024-01-17T12:00:00Z"]`. The 5-field variant
(`"30 9 * * *"`) returns `["2024-01-15T09:30:00Z", "2024-01-16T09:30:00Z"]`. The `@daily`
macro returns `["2024-01-16T00:00:00Z", "2024-01-17T00:00:00Z"]` (next-midnight relative to
the pinned after). An invalid expression like `"0 0 12 32 * *"` short-circuits on the
validate pass: the DSL still succeeds, but `valid = false`, `fireTimes = null`, and
`errorMessage` carries the parser's complaint so the operator message can be routed back
verbatim.

## Exercising OpenTelemetry with `DistributedTracingDsl`

Walks the seven `otel` modes: `span` (start, returns the traceparent), `addEvent`,
`setBaggage` / `getBaggage` (local baggage), `injectContext` / `extractContext` over a simulated
HTTP carrier, and `endSpan`. Note that helper-initiated spans are independent top-level spans — the
helper does not thread ambient OTel context between invocations, so there is no parent-child
chaining across the calls.

See `backend/dsl-starter/dsl-examples/src/dsl/DistributedTracingDsl.java`:

```java
var spanResult = ctx.runHelper("otel", new OtelIn(
    "span", "distributed-trace-example", null, null, null, null, null, null, null, null));
String traceparent = (String) spanResult.as(OtelOut.class).result();
ctx.runHelper("otel", new OtelIn("addEvent", null, null, traceparent, null, null,
    "example.event", null, null, null));
ctx.runHelper("otel", new OtelIn("setBaggage", null, null, null, null, null, null,
    "businessKey", in.businessKey(), null));
Map<String, String> outbound = Map.of("traceparent", traceparent);
ctx.runHelper("otel", new OtelIn("injectContext", null, null, null, null, null, null,
    null, null, outbound));
ctx.runHelper("otel", new OtelIn("endSpan", null, null, traceparent, "OK", null, null,
    null, null, null));
```
