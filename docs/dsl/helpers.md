# DSL Helpers Cookbook

Task-oriented recipes for the built-in `@Helper` catalog. Where [`examples.md`](examples.md)
covers authoring whole DSL *definitions* (the Java source in `dsl-examples/`), this page covers
**calling helpers from inside a Process, Transaction, or Function** to solve concrete problems.

- Every helper is invoked the same way:
  `ctx.runHelper("<name>", new <Name>In(...)).as(<Name>Out.class)`.
- The machine-readable list of every registered helper, its input/output records, and its
  side-effect classification is the **helper catalog** exposed at `GET /api/dsl/helpers`
  (proxied by the admin UI). This page is the human companion to that catalog.
- Why helpers are wired through a generated SPI rather than reflection:
  [`../adr/0002-helper-spi.md`](../adr/0002-helper-spi.md). Where they sit in the runtime:
  [`../architecture-backend.md`](../architecture-backend.md) → *Runtime layers* and
  [`runtime.md`](runtime.md#helper-and-spring-integration).

Recipes are grouped by **what you are trying to do**, not by helper name.

---

## Identity & idempotency

### Generate a sortable, unique key

`uuidV7` returns a UUIDv7 — time-ordered, so keys sort by creation time and index well.

```java
UuidV7Out key = ctx.runHelper("uuidV7", new UuidV7In(null)).as(UuidV7Out.class);
```

### Derive a stable idempotency key for a downstream call

Pass a namespace so repeated runs of the same logical operation collide deterministically within
the same millisecond window (see the helper's javadoc for the exact guarantee):

```java
UuidV7Out idemKey = ctx.runHelper("uuidV7", new UuidV7In("payments/charge/v1"))
        .as(UuidV7Out.class);
```

---

## Encoding

### Base64 a value for a header or token

`base64` with `mode` `"encode"` / `"decode"`; set `urlSafe = true` for the `-_` alphabet (JWT
segments, signed-URL parameters).

```java
Base64Out jwtHeader = ctx.runHelper("base64",
        new Base64In("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", "encode", true))
        .as(Base64Out.class);
```

### Percent-encode a query parameter

`urlEncode` defaults to RFC 3986 semantics (space → `%20`, literal `+` preserved). Set
`form = true` for `application/x-www-form-urlencoded` bodies (space → `+`).

```java
UrlEncodeOut state = ctx.runHelper("urlEncode",
        new UrlEncodeIn(rawState, null, false))
        .as(UrlEncodeOut.class);

String authorizeUrl = "https://auth.example.com/authorize?response_type=code"
        + "&client_id=" + clientId
        + "&state=" + state.result();
```

`urlDecode` is the inverse, with the same `charset` / `form` arguments.

---

## Hashing

### Content fingerprint / ETag

`sha256` hashes the UTF-8 bytes of the input; `encoding` is `"hex"` (default), `"base64"`, or
`"base64url"`. An empty string is valid (it hashes the empty byte sequence); `null` is rejected.

```java
Sha256Out etag = ctx.runHelper("sha256", new Sha256In(responseBody, "hex"))
        .as(Sha256Out.class);

HttpCallOut next = ctx.runHelper("httpCall",
        new HttpCallIn(url, "GET",
                Map.of("If-None-Match", "\"" + etag.result() + "\""),
                null, null, null))
        .as(HttpCallOut.class);
// next.status() == 304  → body unchanged, skip reprocessing
```

### Sign an outbound webhook

`hmacSha256Sign` takes `(message, secret, encoding?)` and returns a keyed digest;
`hmacSha256Verify` takes `(message, secret, signature, encoding?)` and returns a boolean using a
constant-time comparison.

```java
HmacSha256SignOut sig = ctx.runHelper("hmacSha256Sign",
        new HmacSha256SignIn(body, webhookSecret, "hex"))
        .as(HmacSha256SignOut.class);
// send header:  X-Cbs-Signature: sha256=<sig.signature()>
```

### Hex-encode a fingerprint with `hex`

`sha256` already returns lowercase hex by default, but `hex` (`HexIn(input, mode)`) is the
general-purpose primitive when you have a hex string that is *not* a hash digest — a JWT segment,
a content hash coming from another service, an opaque correlation token you need to compare in
DSL land.

```java
HexOut raw = ctx.runHelper("hex", new HexIn(jwtSegment, "decode")).as(HexOut.class);
String original = raw.result();        // back to the original UTF-8 string
```

Pairing `sha256` + `hex` for a content fingerprint when you want both the raw digest and a
re-encoded form (e.g. normalized uppercase for an external system):

```java
Sha256Out digest = ctx.runHelper("sha256", new Sha256In(body, "hex")).as(Sha256Out.class);
HexOut upper = ctx.runHelper("hex", new HexIn(digest.result(), "encode")).as(HexOut.class);
String upperDigest = upper.result().toUpperCase(Locale.ROOT);
```

Empty input is rejected in both `encode` and `decode` modes; odd-length hex and
non-hex characters surface as `IllegalArgumentException` at decode time.

### Chained example — AWS-style canonical request signing

A real signing flow combines three helpers: a formatted timestamp, a payload hash, and the HMAC
over the canonical string.

```java
// 1. amz-date  (currentTimestamp → ISO string → formatDate to the compact AWS form)
CurrentTimestampOut nowIso = ctx.runHelper("currentTimestamp",
        new CurrentTimestampIn("UTC")).as(CurrentTimestampOut.class);

FormatDateOut amzDate = ctx.runHelper("formatDate",
        new FormatDateIn(nowIso.timestamp(), "yyyyMMdd'T'HHmmss'Z'", "UTC"))
        .as(FormatDateOut.class);

// 2. hashed payload
Sha256Out payloadHash = ctx.runHelper("sha256", new Sha256In(requestBody, "hex"))
        .as(Sha256Out.class);

// 3. sign the assembled string-to-sign
String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate.formatted() + "\n"
        + credentialScope + "\n" + payloadHash.result();

HmacSha256SignOut signature = ctx.runHelper("hmacSha256Sign",
        new HmacSha256SignIn(stringToSign, signingKey, "hex"))
        .as(HmacSha256SignOut.class);
```

---

## Text processing

### Extract a substring with `regex`

`regex` `op` is `"match"`, `"extract"`, `"replace"`, or `"split"`. `RegexIn` is
`(op, pattern, input, replacement, group, flags)`. `match` uses `Matcher.find()` (matches
anywhere), not a whole-string match. Patterns are compiled once and held in a bounded LRU cache.

```java
RegexOut level = ctx.runHelper("regex",
        new RegexIn("extract", "\\b(ERROR|WARN|INFO)\\b", logLine, null, 1, null))
        .as(RegexOut.class);
String value = level.value();          // captured group 1, or "" on no match
```

### Validate an identifier

```java
RegexOut ok = ctx.runHelper("regex",
        new RegexIn("match", "^[A-Z]{3}-\\d{4,8}$", requestId, null, null, null))
        .as(RegexOut.class);
if (!Boolean.TRUE.equals(ok.matched())) {
    // reject
}
```

### Fill a message template

`formatMessage` takes `(template, params)` — a named-placeholder template plus a `Map`.

```java
FormatMessageOut msg = ctx.runHelper("formatMessage",
        new FormatMessageIn("Run {runId} for {customer} failed at step {step}",
                Map.of("runId", runId, "customer", name, "step", step)))
        .as(FormatMessageOut.class);
```

---

## Date & time

### Format a timestamp for an HTTP header

`formatDate` takes `(input, pattern, zone)` where `input` is ISO-8601 or epoch-millis, `zone`
defaults to UTC. Preset pattern aliases (`ISO_INSTANT`, `RFC_1123_DATE_TIME`, …) are accepted
alongside custom patterns.

```java
FormatDateOut ifModifiedSince = ctx.runHelper("formatDate",
        new FormatDateIn(lastSeenIso, "RFC_1123_DATE_TIME", "UTC"))
        .as(FormatDateOut.class);
// header:  If-Modified-Since: <ifModifiedSince.formatted()>
```

### Parse a partner-API date string

`parseDate` is the inverse — `(input, pattern, zone)` → ISO-8601 string.

```java
ParseDateOut parsed = ctx.runHelper("parseDate",
        new ParseDateIn(partnerDate, "yyyy-MM-dd'T'HH:mm:ssXXX", "UTC"))
        .as(ParseDateOut.class);
String iso = parsed.iso();
```

### Compute a "next business day" deadline

`dateMath` covers add / diff / before / after / startOf behind a single `op` discriminator, so
`DateMathIn(op, date, end, amount, unit, zone)` carries every variant. The output record has
exactly one populated field per op: `value` (string for `add` / `startOf`), `number` (long for
`diff`), `flag` (boolean for `before` / `after`). Months and years use calendar arithmetic — Jan
31 + 1 month → Feb 28, Feb 29 + 1 year → Feb 28. Days and weeks use
`ZonedDateTime.plus(amount, ChronoUnit)`, which preserves the wall clock across DST (so
`add("2026-03-08T06:00:00Z", 1, "days", "America/New_York")` lands on `2026-03-09T05:00:00Z`
— 23 h, not 24 h).

```java
// "Ship by end of next business day" — add a calendar day, then re-check day-of-week.
DateMathOut plusOne = ctx.runHelper("dateMath",
        new DateMathIn("add", orderTimestamp, null, 1L, "days", "America/New_York"))
        .as(DateMathOut.class);
DateMathOut atStart = ctx.runHelper("dateMath",
        new DateMathIn("startOf", plusOne.value(), null, null, "day", "America/New_York"))
        .as(DateMathOut.class);
// shipDeadline = atStart.value() adjusted forward if it falls on Saturday / Sunday.
```

### Detect a stale order

```java
DateMathOut ageHours = ctx.runHelper("dateMath",
        new DateMathIn("diff", orderPlacedAt, currentTimestamp, null, "hours", "UTC"))
        .as(DateMathOut.class);
if (ageHours.number() > 24) {
    // escalate, refund, etc.
}
```

`dateMath.before` / `dateMath.after` compare two timestamps and return a boolean in `flag` —
useful for cutoff checks (`before(cutoff, now)`), idempotency windows, or cache-staleness
guards. `dateMath.startOf` truncates to `minute` / `hour` / `day` / `month` / `year`, which is
the safe primitive for building cache keys (`startOf(now, "hour")`) or partition boundaries
(`startOf(now, "day")`) without hand-formatting.

---

## HTTP integration

`httpCall` (`HttpCallIn(url, method, headers, body, queryParams, timeoutMillis)`) is the one
helper with a real external side effect. Combine it with the encoders/hashers above rather than
hand-building canonical strings.

Common shape — sign, then send:

```java
HmacSha256SignOut sig = ctx.runHelper("hmacSha256Sign",
        new HmacSha256SignIn(body, secret, "hex")).as(HmacSha256SignOut.class);

HttpCallOut resp = ctx.runHelper("httpCall",
        new HttpCallIn("https://api.partner.example.com/events", "POST",
                Map.of("Content-Type", "application/json",
                       "X-Cbs-Signature", "sha256=" + sig.signature()),
                body, null, null))
        .as(HttpCallOut.class);
```

Pull a field out of the response with `jsonExtract` (`JsonExtractIn(json, path)`):

```java
JsonExtractOut orderId = ctx.runHelper("jsonExtract",
        new JsonExtractIn(resp.body(), "$.data.orderId"))
        .as(JsonExtractOut.class);
if (orderId.present()) { /* use orderId.value() */ }
```

### Validate an outbound payload with `validateJson`

`validateJson` (`ValidateJsonIn(payload, schema)`) checks a JSON string against a JSON Schema
object and returns the list of validation errors plus a `valid` flag. It has no side effect, so
it is safe to call in Preview mode.

```java
ValidateJsonOut check = ctx.runHelper("validateJson",
        new ValidateJsonIn(body, "{\"type\":\"object\",\"required\":[\"orderId\",\"amount\"]}"))
        .as(ValidateJsonOut.class);

if (!check.valid()) {
    List<ValidationError> errors = check.errors();
    // surface errors before making the downstream call
}

HttpCallOut resp = ctx.runHelper("httpCall",
        new HttpCallIn("https://api.partner.example.com/events", "POST",
                Map.of("Content-Type", "application/json"), body, null, null))
        .as(HttpCallOut.class);
```

In **Preview mode** `httpCall` is intercepted and recorded, not sent — see
[`preview-mode.md`](preview-mode.md).

---

## Observability

- **Log the helper *inputs that matter*, never secrets.** Log the `url` and `method` of an
  `httpCall`, not the `Authorization` header or an HMAC `secret`.
- **Put helper failures on the error path with context.** Helper calls return a `Result`; on
  failure, surface the helper name and the offending argument shape (e.g. `"parseDate: pattern
  'yyyy/MM/dd' did not match input"`), not just the stack trace.
- **Preview/Explain runs are instrumented for free** — `dsl.preview.calls` (tagged by `kind`)
  and `dsl.preview.external.calls` (tagged by `type`) count helper and external-call activity.
  See [`../architecture-backend.md`](../architecture-backend.md) → *Observability & operations*.

---

## Retry timing

### Compute exponential backoff before retrying

`backoff` computes a delay in milliseconds without blocking. Use the returned value to configure a
Temporal `Timer` in a workflow, or `Thread.sleep` in preview/dev code; jitter is intentionally
non-deterministic and must not be replayed as workflow state.

```java
BackoffOut delay = ctx.runHelper("backoff",
        new BackoffIn(attempt, 1000L, 60000L, "full", null))
        .as(BackoffOut.class);
// Temporal Timer / Thread.sleep(delay.delayMillis())
// retry with httpCall, then validateJson the response
```

Supported jitter strategies are `none`, `full`, `equal`, and `decorrelated`. The latter accepts a
previous delay through `previousDelay` and is useful when retry contention is high.

---

## Adding a helper to this page

### extractXml

`extractXml` (`XmlExtractIn(xml, xpath)`) returns the text of the first node matched by an XPath
1.0 expression, with a `present` flag. It has no side effect and is safe in Preview mode. The XML
parser is hardened against XXE — payloads containing a `<!DOCTYPE>` declaration are rejected.

```java
XmlExtractOut sessionId = ctx.runHelper("extractXml",
        new XmlExtractIn(soapResponse, "//SessionId/text()"))
        .as(XmlExtractOut.class);
if (sessionId.present()) {
    String id = sessionId.value();
}
```

## CSV in / CSV out

`parseCsv` turns an RFC 4180 CSV payload into a list of rows; `formatCsv` turns rows back into a
CSV string. Use `CsvOptions(delimiter, withHeader, lineSeparator)` to override the defaults:
`,` (first character only), `false`, and `\r\n`.

```java
ParseCsvOut data = ctx.runHelper("parseCsv",
        new ParseCsvIn(payload, new CsvOptions(",", false, "\r\n")))
        .as(ParseCsvOut.class);

List<List<String>> rows = data.rows();
```

`withHeader = true` drops the first row so you can treat it as headers:

```java
ParseCsvOut body = ctx.runHelper("parseCsv",
        new ParseCsvIn(csv, new CsvOptions(null, true, null)))
        .as(ParseCsvOut.class);
```

`formatCsv` prepends an optional `headerRow` and enforces strict rectangular output:

```java
FormatCsvOut csv = ctx.runHelper("formatCsv",
        new FormatCsvIn(rows, headerRow, new CsvOptions("\t", false, "\n")))
        .as(FormatCsvOut.class);
```

## YAML in / YAML out

`parseYaml` turns a YAML 1.2 document into a nested `Map<String, Object>` (maps stay maps,
sequences become `List<Object>`, scalars become strings/numbers/booleans/null). `formatYaml` is
the inverse — any nested `Map`/`List`/scalar tree serializes back to a canonical block-style
YAML string with two-space indent.

```java
ParseYamlOut manifest = ctx.runHelper("parseYaml",
        new ParseYamlIn(k8sPayload)).as(ParseYamlOut.class);
Map<String, Object> spec = (Map<String, Object>) manifest.data().get("spec");
int replicas = (Integer) spec.get("replicas");
```

```java
FormatYamlOut yaml = ctx.runHelper("formatYaml",
        new FormatYamlIn(Map.of("spec", Map.of("replicas", 3, "image", "nginx:1.27"))))
        .as(FormatYamlOut.class);
// yaml.yaml() == "spec:\n  replicas: 3\n  image: nginx:1.27\n"
```

YAML 1.2 semantics are enforced: only `true`/`false` parse as booleans — `yes`, `no`, `on`, and
`off` remain plain strings (the YAML 1.1 trap). The loader is hardened against snakeyaml
CVE-2017-18640 — a payload like `!!javax.scripting.ScriptEngineManager {}` is refused before any
class is instantiated (`LoaderOptions` `TagInspector` rejects every global tag, plus
`setAllowDuplicateKeys(false)`, `setMaxAliasesForCollections(50)`, and a 3 MiB
`setCodePointLimit`).

## Metrics

`metric` registers a Micrometer meter against the host application's `MeterRegistry` bean (the
same registry that the built-in `MetricsStage` writes its call / duration / external-call meters
into). Four `type` values are supported, picked by a discriminator field on the input record:

- `"counter"` — increments a `Counter` by `amount` (default `1`).
- `"gauge"` — sets a `Gauge` backed by an in-memory holder; last call with the same `name` + `tags` wins.
- `"timer"` — records a `Timer` of `durationMs` (non-negative).
- `"summary"` — records `value` into a `DistributionSummary` (useful for payload sizes, token
  counts, raw latencies that are already in milliseconds, etc.).

### Emit a domain KPI on a successful outcome

```java
// After a successful "place order" branch:
ctx.runHelper("metric",
        new MetricIn("counter", "orders.placed.count", Map.of("channel", "web"), null, 1L, null))
        .as(MetricOut.class);
```

### Count partner API timeouts (alongside the `httpCall` call site)

```java
try {
    ctx.runHelper("httpCall", new HttpCallIn(...));
} catch (HttpCallTransportException e) {
    ctx.runHelper("metric",
            new MetricIn("counter", "partnerapi.timeout.count",
                    Map.of("partner", "acme"), null, 1L, null))
            .as(MetricOut.class);
    throw e;
}
```

### Track partner API latency as a distribution summary

```java
long started = System.nanoTime();
try {
    ctx.runHelper("httpCall", new HttpCallIn(...));
} finally {
    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
    ctx.runHelper("metric",
            new MetricIn("summary", "partnerapi.latency_ms",
                    Map.of("partner", "acme"), (double) elapsedMs, null, null))
            .as(MetricOut.class);
}
```

`MetricOut.emitted` is `false` when the host application does not provide a `MeterRegistry` bean
(e.g. it does not pull in the Spring Boot actuator starter) — the helper validates the input and
no-ops, so the DSL above is safe to call unconditionally. Tag keys must not be `null`; tag values
that are `null` are coerced to the empty string (Micrometer's `Tag.of` rejects null values).
