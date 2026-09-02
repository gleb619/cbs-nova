# How to work with DSL examples

The `backend/dsl-examples` module contains real-world DSL definitions. They are written as
[JEP-512 compact source files](https://openjdk.org/jeps/512) and compiled into Temporal
workflows/activities at build time.

## Where the examples live

```
backend/dsl-examples/src/
├── BatchProcessingDsl.java
├── ExceptionProbeDsl.java
├── InvoiceGenerationDsl.java
├── LongWorkSimulationDsl.java
├── NestedCompensationDsl.java
├── OrderSagaDsl.java
├── SampleProcessDsl.java
├── SimpleGreetingDsl.java
├── SimpleOrderDsl.java
├── SimpleValidationDsl.java
└── ...
```

Every file exposes a `List<DslObject> define()` method built with the fluent DSL API.

## Building the examples

The `compileDsl` Gradle task scans `dsl-examples/src`, loads the definitions, validates them,
and generates Temporal classes under `dsl-examples/build/generated`.

```bash
cd backend
./gradlew :dsl-examples:compileDsl
```

After a successful run you will find generated classes such as:

```
backend/dsl-examples/build/generated/cbs/nova/dsl/generated/batchprocessing/v1/
├── BatchProcessingProcessWorkflow.java
└── BatchProcessingProcessDefinition.java
```

The generated package is `cbs.nova.dsl.generated.<name>.<version>` where `<name>` is the
process/transaction name lower-cased and `<version>` is the version declared in the DSL
(default `v1`).

## Running the integration test

`backend/starter-example` contains a Testcontainers-based integration test that starts a real
Temporal server plus PostgreSQL, registers the generated `BatchProcessing` worker, and runs
the workflow end-to-end.

```bash
cd backend
./gradlew :starter-example:test \
  --tests cbs.nova.dsl.example.integration.BatchProcessingDslIntegrationTest
```

The test does the following:

1. Starts `postgres:15` and `temporalio/auto-setup:1.25.2` containers on a shared Docker
   network.
2. Loads `dsl-examples/src` with `DefinitionLoader` into a fresh `GlobalManager`.
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

1. Create a compact source file in `backend/dsl-examples/src/`.
2. Use `Dsl.process(...)`, `Dsl.transaction(...)`, or `Dsl.function(...)` inside `define()`.
3. Declare `.input(...)` / `.output(...)` when the workflow needs typed payloads.
4. Run `./gradlew :dsl-examples:compileDsl` to validate generation.
5. Optionally add an integration test in `backend/starter-example/src/test/java` that loads
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
