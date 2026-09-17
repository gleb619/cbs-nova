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

### Fill an operator-supplied template (no expression evaluation)

`interpolate` is the safe sibling of `formatMessage`: literal `${key}` substitution, no SpEL.
Where `formatMessage` evaluates the template as a single expression (powerful but unsafe for
templates that originate as operator or user config — Epic 3 notification rules), `interpolate`
treats the template as data: `$$` escapes a literal `$`, keys are looked up in the params `Map`,
and a key absent from the map follows `onMissing` — `error` (default, fails), `empty`
(substitutes `""`), or `keep` (leaves `${key}` verbatim). A value present in the map but mapped
to `null` always renders as `""` (missing ≠ null). Phase 1 supports flat keys only — dotted
paths (`${order.id}`) and default-value syntax (`${name:-anon}`) are deliberate follow-ups.

```java
InterpolateOut msg = ctx.runHelper("interpolate",
        new InterpolateIn(
                "Run ${runId} for ${customer} failed at step ${step}",
                Map.of("runId", runId, "customer", name, "step", step),
                "error"))
        .as(InterpolateOut.class);
String body = msg.result();                       // rendered template
List<String> touched = msg.resolvedKeys();        // distinct keys, first-seen order
```

## JSON object merge patches

`jsonPatch` applies RFC 7396 JSON Merge Patch in two directions. The `mode` discriminator
selects `"apply"` (merge `patch` into `source`) or `"diff"` (compute the merge-patch
document that turns `source` into `target`). Both arguments must be JSON object strings —
arrays or scalars surface `IllegalArgumentException("jsonPatch requires object JSON, got: ARRAY")`
and malformed JSON surfaces a `"jsonPatch: invalid JSON in …"` message. Both modes return a
compact (no-whitespace) JSON object string in `JsonPatchOut.result`.

### Apply a partial update to a stored object

A `null` value in the patch removes the key (RFC 7396 §3), nested object values merge
recursively, and array leaves are replaced as-is.

```java
JsonPatchOut merged = ctx.runHelper("jsonPatch",
        new JsonPatchIn(currentJson,
                "{\"status\":\"shipped\",\"shippedAt\":\"2026-03-09T10:00:00Z\"}",
                null, "apply"))
        .as(JsonPatchOut.class);
String next = merged.result();
```

### Compute the patch between two states

```java
JsonPatchOut patch = ctx.runHelper("jsonPatch",
        new JsonPatchIn(beforeJson, null, afterJson, "diff"))
        .as(JsonPatchOut.class);
// patch.result() == "{}"                                  when before == after
// patch.result() == "{\"a\":null}"                        when 'a' was removed
// patch.result() == "{\"a\":{\"x\":2,\"y\":3},\"b\":2}"    for nested object diffs
```

## List & record operations

`listOps` packs eight `mode` values behind one record shape. The discriminator is matched
case-insensitively, and the helper picks the source argument it needs (`records`,
`list`, or `nested`) based on the mode. `ListOpsOut.result` is typed as `Object` — its
concrete shape depends on the mode (`List<Object>`, `Map<Object, List<Map<…>>>`, `Double`,
`Map<String, Object>`, etc.).

### Pluck a column out of a list of records

`pluck` walks `records` and pulls the named `field` from each one, in order. A missing key
yields `IllegalArgumentException` naming the offending index — the message uses
`"index <n>"`.

```java
ListOpsOut names = ctx.runHelper("listOps",
        new ListOpsIn("pluck", users, null, null, "name", null))
        .as(ListOpsOut.class);
List<Object> values = (List<Object>) names.result();
```

### Group, count, and aggregate

`groupBy` returns a `Map<Object, List<Map<String, Object>>>` preserving first-seen group
order. `countBy` returns a `Map<Object, Long>` of frequency counts in first-seen order.
`sumBy` returns a `Double` total. `minBy` and `maxBy` return the winning record as a
`Map<String, Object>` (ties broken by first occurrence).

```java
ListOpsOut grouped = ctx.runHelper("listOps",
        new ListOpsIn("groupBy", orders, null, null, "region", null))
        .as(ListOpsOut.class);
Map<Object, List<Map<String, Object>>> byRegion =
        (Map<Object, List<Map<String, Object>>>) grouped.result();

ListOpsOut freq = ctx.runHelper("listOps",
        new ListOpsIn("countBy", orders, null, null, "status", null))
        .as(ListOpsOut.class);
Map<Object, Long> counts = (Map<Object, Long>) freq.result();

ListOpsOut total = ctx.runHelper("listOps",
        new ListOpsIn("sumBy", orders, null, null, "amount", null))
        .as(ListOpsOut.class);
Double sum = (Double) total.result();

ListOpsOut top = ctx.runHelper("listOps",
        new ListOpsIn("maxBy", orders, null, null, "amount", null))
        .as(ListOpsOut.class);
Map<String, Object> winner = (Map<String, Object>) top.result();
```

`sumBy`, `minBy`, and `maxBy` reject empty `records` (`"…: records is empty"`) and a
non-numeric `field` value (`"non-numeric value at record index <n>"`).

### Flatten or deduplicate a list

`flatten` recursively unrolls nested `List<?>` to the requested `depth` (`1` by default;
`-1` means "fully flatten"). Scalars pass through unchanged at every level — mixed
scalar/list input is accepted without error. `distinct` dedupes via `LinkedHashSet`,
preserving first-seen insertion order across mixed-type elements.

```java
ListOpsOut flat = ctx.runHelper("listOps",
        new ListOpsIn("flatten", null, null, nested, null, -1))
        .as(ListOpsOut.class);

ListOpsOut unique = ctx.runHelper("listOps",
        new ListOpsIn("distinct", null, items, null, null, null))
        .as(ListOpsOut.class);
```

### Filter records by field equality

Where `listOps` `groupBy`/`countBy` aggregate, `filterRecords` just selects: it keeps the records
whose `field` equals `value` (`Objects.equals` semantics, so `null` matches `null`) and drops the
rest. `null` records yield an empty result rather than an error.

```java
FilterRecordsOut active = ctx.runHelper("filterRecords",
        new FilterRecordsIn(orders, "status", "active"))
        .as(FilterRecordsOut.class);
List<Map<String, Object>> matched = active.matched();
```

### Sort records by a field

`sortRecords` orders `records` by `field` and returns the full list (nothing is dropped).
Direction comes from `direction` (`"asc"`/`"desc"`) when set, otherwise from the `ascending`
boolean (`true` by default). The comparison `algorithm` is:

- `"natural"` (default) — `Comparable` values of the same class compare natively; anything else
  falls back to string comparison.
- `"string"` — everything is compared as `String.valueOf(...)`, lexicographic.
- `"numeric"` — values are coerced through `BigDecimal` (numbers and numeric strings both work).

Records with a `null` field value sort last regardless of direction; `null`/empty `records`
return empty.

```java
// "100" / "20" / "3" sort by numeric value, not lexicographically:
SortRecordsOut byAmount = ctx.runHelper("sortRecords",
        new SortRecordsIn(orders, "amount", true, "numeric", null))
        .as(SortRecordsOut.class);
List<Map<String, Object>> sorted = byAmount.records();

// Newest first via the direction override:
SortRecordsOut newest = ctx.runHelper("sortRecords",
        new SortRecordsIn(orders, "createdAt", true, null, "desc"))
        .as(SortRecordsOut.class);
```

## Object shaping

### Shape an outbound payload with `pick`

`pick` projects a single record down to a key allowlist; `omit` mode inverts it to a
 denylist. Handy right before an outbound `httpCall`: send only the fields the downstream
 expects, or strip internal bookkeeping fields.

```java
// Send only the fields the partner API expects, in that order:
PickOut payload = ctx.runHelper("pick",
        new PickIn(order, List.of("id", "amount", "currency"), "pick"))
        .as(PickOut.class);
HttpCallOut response = ctx.runHelper("httpCall",
        new HttpCallIn("https://partner.example/orders", "POST", null, payload.result(), null))
        .as(HttpCallOut.class);

// Or strip internal fields instead of listing the public ones:
PickOut publicView = ctx.runHelper("pick",
        new PickIn(order, List.of("internalNotes", "costBasis"), "omit"))
        .as(PickOut.class);
```

`mode` is matched case-insensitively and defaults to `"pick"` when null/blank. In `pick`
mode the result holds each listed key that is present in `source`, in `keys` order —
absent keys are silently skipped (not null-filled), while a key present with a `null` value
is kept (`null` is a value, not absence). In `omit` mode you get a copy of `source`
(insertion order preserved) minus every listed key. The input map is never mutated, and
nested values are copied by reference (shallow — deep projection is a follow-up).
`null` `source` fails (`"pick.source is required"`); `null`/empty `keys` fails
(`"pick.keys must be non-empty"`). Only flat top-level keys — dotted paths (`"a.b"`) are a
follow-up.

## Numeric aggregations

`arithmetic` (backward-compatible alias `math`) covers numeric aggregations (`sum`, `min`,
`max`, `mean`, `median`, `percentile`, `stddev`) plus scalar transforms (`clamp`, `round`,
`abs`, `floor`, `ceil`). The `mode` discriminator is matched case-insensitively.
`ArithmeticOut.result` is `Double` for most operations and `Long` for `floor` and `ceil`.
Aggregation modes take `numbers`; the scalar modes take `value` (plus `min`/`max` for
`clamp`, `scale` for `round`).

The helper also accepts the original `sumValues` calling shape (`operation` with `values`)
for backward compatibility.

### Summarize a series of measurements

`percentile` uses linear interpolation between adjacent sorted values (NumPy `"linear"` /
Hyndman-Fan type 7); `p` must be in `[0, 100]` inclusive. `stddev` is sample standard
deviation (Bessel-corrected, divided by `N-1`) and requires at least two elements.

```java
ArithmeticOut mean = ctx.runHelper("arithmetic",
        new ArithmeticIn("mean", null, latencies, null, null, null, null, null, null))
        .as(ArithmeticOut.class);
Double avgMs = (Double) mean.result();

ArithmeticOut p99 = ctx.runHelper("arithmetic",
        new ArithmeticIn("percentile", null, latencies, null, null, null, null, null, 99.0))
        .as(ArithmeticOut.class);
// For [1..100], p99 -> 99.01 via linear interpolation.
```

### Clamp and round

`round` uses `BigDecimal` with `RoundingMode.HALF_UP` semantics and accepts `scale` in
`[-1, 15]` (default `0`). `floor` and `ceil` return a `Long`.

```java
ArithmeticOut bounded = ctx.runHelper("arithmetic",
        new ArithmeticIn("clamp", null, null, null, rawScore, 0, 100, null, null))
        .as(ArithmeticOut.class);

ArithmeticOut rounded = ctx.runHelper("arithmetic",
        new ArithmeticIn("round", null, null, null, 3.14159, null, null, 2, null))
        .as(ArithmeticOut.class);
// rounded.result() == 3.14
```

A non-numeric element inside `numbers` surfaces an `IllegalArgumentException` that
includes the offending index — useful when an upstream payload has been corrupted mid-stream.

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

### Parse a duration string

`parseDuration` (`ParseDurationIn(value)`) turns a human/config duration string into
total milliseconds, floored seconds, and a normalized ISO-8601 duration string.

| Form | Example | `millis` | `seconds` | `iso` |
|---|---|---|---|---|
| ISO-8601 hours + minutes | `PT1H30M` | `5400000` | `5400` | `PT1H30M` |
| ISO-8601 days + hours | `P2DT3H` | `183600000` | `183600` | `PT51H` |
| ISO-8601 bare days | `P2D` | `172800000` | `172800` | `PT48H` |
| ISO-8601 fractional seconds | `PT0.5S` | `500` | `0` | `PT0.5S` |
| Shorthand single unit | `90m` | `5400000` | `5400` | `PT1H30M` |
| Shorthand compound | `1h30m` | `5400000` | `5400` | `PT1H30M` |
| Shorthand milliseconds | `250ms` | `250` | `0` | `PT0.25S` |

```java
ParseDurationOut delay = ctx.runHelper("parseDuration",
        new ParseDurationIn("1h30m"))
        .as(ParseDurationOut.class);
long delayMillis = delay.millis();   // 5400000
String delayIso = delay.iso();       // "PT1H30M"
```

Shorthand units are `d` (days), `h` (hours), `m` (minutes, **never months**), `s`
(seconds), and `ms` (milliseconds). Compound segments may include whitespace. A bare
number with no unit is rejected as ambiguous — the helper does **not** assume
milliseconds. Signed/negative durations are rejected in phase 1. Overflow past
`Long.MAX_VALUE` milliseconds is rejected cleanly.

---

## Number formatting

### Format a numeric amount for display

`formatNumber` takes `(input, pattern, locale)` where `input` is a `Number` or numeric string
(scientific notation works), `pattern` is either a preset alias or a raw
`java.text.DecimalFormat` pattern, and `locale` is an optional BCP-47 tag defaulting to
`Locale.ROOT`.

Supported preset aliases (case-sensitive):

- `INTEGER` — grouping separator, no fraction digits
- `DECIMAL` — grouping separator + two fraction digits
- `PERCENT` — multiplied by 100 and suffixed with `%`
- `CURRENCY` — locale-specific currency symbol + two fraction digits

Custom patterns are parsed with `DecimalFormat(pattern, DecimalFormatSymbols(locale))`. Rounding
is always `RoundingMode.HALF_UP`. Invalid patterns, invalid locales, `NaN` / `Infinity` and other
non-numeric input surface as a failed `Result` rather than throwing.

```java
FormatNumberOut amount = ctx.runHelper("formatNumber",
        new FormatNumberIn(orderTotal, "CURRENCY", "en-US"))
        .as(FormatNumberOut.class);
// amount.formatted() == "$1,234.50"
```

```java
FormatNumberOut compact = ctx.runHelper("formatNumber",
        new FormatNumberIn(1234567.891, "DECIMAL", "de-DE"))
        .as(FormatNumberOut.class);
// compact.formatted() == "1.234.567,89"
```

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

### Outbound URL validation (SSRF guard)

Before any request is built, `httpCall` validates the URL against the helper-scoped config
bound at `cbs.dsl.helper.http-call` (record `HttpCallProperties`, validator
`cbs.nova.starter.security.OutboundUrlValidator`):

| Key | Default | Meaning |
| --- | --- | --- |
| `allowed-schemes` | `["https", "http"]` | Scheme allowlist. Anything else (e.g. `ftp`, `file`) is rejected. `http` stays in the default so existing DSLs keep working; tighten via config. |
| `block-private-addresses` | `true` | Rejects URLs whose host resolves (via `InetAddress.getAllByName`) to a loopback, link-local, site-local, any-local (wildcard) or multicast address. This blocks cloud-metadata endpoints such as `http://169.254.169.254/...` and internal services such as `http://localhost:8090/actuator/...`. |
| `allowed-hosts` | `[]` (any non-private host) | Optional host allowlist; exact or `*.suffix` match. Empty means no host restriction beyond the private-address block. |

**This is a behaviour change.** With the defaults, any existing DSL that calls an internal
host (loopback, RFC 1918, link-local) now fails with an `IllegalArgumentException`-based
failure. To restore the old behaviour, set:

```yaml
cbs:
  dsl:
    helper:
      http-call:
        block-private-addresses: false
```

To lock a DSL down to known partners instead, leave the private-address block on and set
`allowed-hosts`, e.g. `["api.partner.example.com", "*.example.com"]`.

Failure messages never echo URL userinfo (credentials); the URL is sanitized before it is
included in any rejection reason.

**Redirect caveat.** When `redirectPolicy` is `NORMAL` or `ALWAYS`, the final URI after a
followed redirect is re-validated and the call fails if it is rejected — but the JDK client
has already followed the redirect by then, so this is detection, not prevention (TOCTOU).
For untrusted targets use `redirectPolicy: NEVER` and validate the `Location` header
yourself; full redirect-time enforcement needs a custom redirect interceptor (follow-up).

## HTTP authentication

`httpAuth` builds the header map you attach to an `httpCall`. The `mode` discriminator
selects `bearer`, `basic`, `apiKey`, or `custom` (case-insensitive). `HttpAuthOut.headers`
is a `Map<String, String>` — typically a single entry under `"Authorization"`. All
required-argument failures surface as `IllegalArgumentException`.

### Bearer token (most common)

```java
HttpAuthOut auth = ctx.runHelper("httpAuth",
        new HttpAuthIn("bearer", accessToken, null, null, null, null, null, null))
        .as(HttpAuthOut.class);
// auth.headers() == {"Authorization": "Bearer <token>"}

HttpCallOut resp = ctx.runHelper("httpCall",
        new HttpCallIn(url, "GET", auth.headers(), null, null, null))
        .as(HttpCallOut.class);
```

`token` must be non-blank; internal whitespace is preserved verbatim per RFC 6750 §2.1
(use `regex` or `urlEncode` if you need to sanitize the value).

### Basic auth credentials

```java
HttpAuthOut auth = ctx.runHelper("httpAuth",
        new HttpAuthIn("basic", null, "Aladdin", "open sesame", null, null, null, null))
        .as(HttpAuthOut.class);
// auth.headers() == {"Authorization": "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="}
```

`username` is required (blank/null rejected); `password` may be empty (the helper
base64-encodes `"username:"`). The encoding uses the standard alphabet with padding — not
the URL-safe alphabet — so it pairs with the standard `base64` helper, not the JWT-style
`urlSafe = true` form.

### Vendor API key and custom headers

For `apiKey`, `header` defaults to `"X-Api-Key"` when null; an explicit blank `""` is
rejected. `prefix` defaults to `""`; when non-blank the value is rendered as
`"<prefix> <key>"` (one space). For `custom`, `value` may be empty but `header` must be
non-blank.

```java
// apiKey with default header "X-Api-Key"
HttpAuthOut apiKey = ctx.runHelper("httpAuth",
        new HttpAuthIn("apiKey", null, null, null, "sk_live_xxx", null, null, null))
        .as(HttpAuthOut.class);

// apiKey fanned into an Authorization header as "Bearer <key>"
HttpAuthOut bearer = ctx.runHelper("httpAuth",
        new HttpAuthIn("apiKey", null, null, null, "sk_live_xxx",
                "Authorization", "Bearer", null))
        .as(HttpAuthOut.class);

// arbitrary header / value (blank value allowed)
HttpAuthOut custom = ctx.runHelper("httpAuth",
        new HttpAuthIn("custom", null, null, null, null, "X-Auth-Token", null, token))
        .as(HttpAuthOut.class);
```

## Query string composition

`queryString` builds and parses application/x-www-form-urlencoded bodies behind one
discriminator: `"build"` joins a `Map<String, String>` of params (spaces become `+`, keys
preserved in iteration order) and `"parse"` splits a query string into an ordered
`LinkedHashMap` of percent-decoded entries. An optional leading `?` is stripped on parse.
Null `params`, null keys, and null values all fail with `IllegalArgumentException`.

### Build a query string from a Map

```java
Map<String, String> params = new LinkedHashMap<>();
params.put("q", "hello world");
params.put("page", "1");
QueryStringOut qs = ctx.runHelper("queryString",
        new QueryStringIn("build", params, null))
        .as(QueryStringOut.class);
// qs.result() == "q=hello+world&page=1"

HttpCallOut resp = ctx.runHelper("httpCall",
        new HttpCallIn("https://api.example.com/search", "GET",
                Map.of(), null, (String) qs.result(), null))
        .as(HttpCallOut.class);
```

For an OAuth authorize URL where you need RFC 3986 percent-encoding (space → `%20`,
literal `+` preserved) instead of form encoding, run each value through `urlEncode`
directly rather than going through `queryString`.

### Parse an incoming query string

Segments without `=` are skipped with a warning log — useful for tolerating
trailing-ampersand noise from upstream proxies.

```java
QueryStringOut parsed = ctx.runHelper("queryString",
        new QueryStringIn("parse", null, "?a=1&garbage&b=2"))
        .as(QueryStringOut.class);
Map<String, String> entries = (Map<String, String>) parsed.result();
// entries == {"a":"1","b":"2"}
```

## Tokens & JWT

`jwt` covers RFC 7519 JSON Web Tokens for the symmetric HMAC family (`HS256`, `HS384`,
`HS512`). The `mode` discriminator picks one of `parse`, `verify`, `sign`, or `claim`.
The output shape varies per mode (a `Map<String, Object>` for `parse` / `verify`, the
compact `header.payload.signature` string for `sign`, a single claim value for `claim`).

> **Security:** the `parse` and `claim` modes are **decode-only** — they inspect the
> payload without verifying the signature and MUST NOT be used to make trust decisions.
> Only `verify` recomputes the HMAC and validates `exp` / `nbf`; use it for every
> authentication and authorization flow. The helper unconditionally rejects
> `"alg": "none"` and the `HS256 ↔ HS384` alg-confusion class (CVE-2015-9235) by
> requiring the token's header `alg` to match the requested `algorithm` exactly
> (case-sensitive).

### Sign and verify round-trip

`sign` builds a defensive copy of the caller's `payload` map, overwrites any pre-existing
`iat` / `exp` with `iat = now` and `exp = now + ttlSeconds` (default 3600, must be
non-negative), and emits the compact `header.payload.signature` string. `algorithm`
defaults to `HS256`.

```java
Map<String, Object> claims = new LinkedHashMap<>();
claims.put("sub", "alice");
claims.put("role", "admin");

JwtOut signed = ctx.runHelper("jwt",
        new JwtIn("sign", null, "super-secret-key", "HS256", 3600L, claims, null))
        .as(JwtOut.class);
String token = (String) signed.result();

JwtOut verified = ctx.runHelper("jwt",
        new JwtIn("verify", token, "super-secret-key", "HS256", null, null, null))
        .as(JwtOut.class);
Map<String, Object> payload = (Map<String, Object>)
        ((Map<String, Object>) verified.result()).get("payload");
```

`verify` rejects on signature mismatch (`"jwt.verify: signature mismatch"`), expired
`exp` (`"jwt.verify: token expired"`), not-yet-valid `nbf` (`"jwt.verify: token not yet valid"`),
a missing/non-string header `alg`, or a token header `alg` that does not match the
caller's `algorithm` argument.

### Decode-only inspection

Use `parse` and `claim` for telemetry, routing, or surfacing `iat` / `exp` in a debug
payload — anything that does not authorize the caller.

```java
// Pull a single claim WITHOUT verifying the signature.
JwtOut subResult = ctx.runHelper("jwt",
        new JwtIn("claim", token, null, null, null, null, "sub"))
        .as(JwtOut.class);
// subResult.result() == "alice"

// Or pull the full header/payload map (signature segment exposed but not verified).
JwtOut parsed = ctx.runHelper("jwt",
        new JwtIn("parse", token, null, null, null, null, null))
        .as(JwtOut.class);
Map<String, Object> header  = (Map<String, Object>)
        ((Map<String, Object>) parsed.result()).get("header");
Map<String, Object> payload = (Map<String, Object>)
        ((Map<String, Object>) parsed.result()).get("payload");
String rawSignature = (String) ((Map<String, Object>) parsed.result()).get("signature");
```

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

## Compression

`compression` packs four modes behind one record: `gzip` / `gunzip` and
`deflate` / `inflate`. All four operate on the UTF-8 bytes of `input`. Compressed output
is base64-encoded; decompressed output is the UTF-8 string of the recovered bytes. The
`level` field (`0`-`9`; default `-1` for `Deflater.DEFAULT_COMPRESSION`) applies only to
the compress directions. Malformed base64, truncated streams, or an out-of-range `level`
all surface `IllegalArgumentException`.

### Round-trip a payload

```java
CompressionOut encoded = ctx.runHelper("compression",
        new CompressionIn("gzip", payload, -1))
        .as(CompressionOut.class);
String storedInRedis = encoded.result();

CompressionOut decoded = ctx.runHelper("compression",
        new CompressionIn("gunzip", storedInRedis, null))
        .as(CompressionOut.class);
String recovered = decoded.result();
```

`level = 9` produces a smaller-or-equal result than `level = 1` for repetitive input —
use the higher level when caching a payload for reuse, the lower level when generating
once and reading once. `deflate` / `inflate` use the zlib wrapper (matching the typical
HTTP `Content-Encoding: deflate` convention); `gzip` / `gunzip` produce a standalone
gzip container usable from the shell.

## Randomness

`random` generates non-cryptographic pseudo-random values backed by `ThreadLocalRandom`.
Use it for sample data, ids, and load-test jitter — not for secrets, tokens, or any
security-sensitive use case. The `mode` discriminator picks `int`, `long`, `double`,
`string`, or `choice`. Bounds are inclusive for `int` / `long` and half-open
(`[min, max)`) for `double`.

### Random ids, jitters, and choice

```java
RandomOut id = ctx.runHelper("random",
        new RandomIn("int", 1000, 9999, null, null, null, null, null, null, null))
        .as(RandomOut.class);
Integer requestId = (Integer) id.result();

// Short hex string for a trace span id
RandomOut span = ctx.runHelper("random",
        new RandomIn("string", null, null, null, null, null, null, 16, "hex", null))
        .as(RandomOut.class);
String spanId = (String) span.result();

// Pick a region for a canary release
RandomOut picked = ctx.runHelper("random",
        new RandomIn("choice", null, null, null, null, null, null, null, null,
                List.of("us-east", "eu-west", "ap-south")))
        .as(RandomOut.class);
```

`string` accepts charset `alphanumeric` (default), `alpha`, `numeric`, `hex`, or
`base64url`. `length` must be in `[0, 100000]`; a length of `0` returns the empty string.
`choice` rejects a null or empty `list` (`"random.choice.list must not be empty"`).

## Secrets and tokens

`secret` generates cryptographic random values backed by `SecureRandom` — the helper to use
for API keys, signing secrets, session tokens, and idempotency keys. Unlike `random` (which
is explicitly NOT safe for secrets), every value here comes from a CSPRNG. The `mode`
discriminator picks `bytes` or `token`; both take `length` in `[0, 100000]`.

### Random bytes and URL-safe tokens

```java
// 32 random bytes as lowercase hex (default encoding) — an API key
SecretOut key = ctx.runHelper("secret",
        new SecretIn("bytes", 32, null))
        .as(SecretOut.class);
String apiKey = key.result();

// Random bytes as base64url — a signing secret for HMAC
SecretOut signing = ctx.runHelper("secret",
        new SecretIn("bytes", 32, "base64url"))
        .as(SecretOut.class);

// URL-safe opaque token: 43 chars from the 64-char base64url alphabet
// (A-Z a-z 0-9 - _), 6 bits of entropy per character = 258 bits total
SecretOut session = ctx.runHelper("secret",
        new SecretIn("token", 43, null))
        .as(SecretOut.class);
String sessionToken = session.result();
```

`bytes` encodes `length` random bytes as `"hex"` (default), `"base64"`, or `"base64url"`
(selected by `encoding`, case-insensitive); an unknown encoding is rejected
(`"secret.bytes.encoding must be one of hex, base64, base64url, was: ..."`). `token` draws
`length` characters uniformly from the base64url alphabet — no padding, URL-safe by
construction — giving exactly 6 bits of entropy per character, so `length = 32` already
yields 192 bits. Out-of-range lengths are rejected with `IllegalArgumentException`
(`"secret.<mode>.length must be >= 0, was: ..."` / `"... must be <= 100000, was: ..."`).

## Versioning

`semver` parses, compares, range-checks, bumps, and formats SemVer 2.0.0 versions
([semver.org](https://semver.org)). The `mode` discriminator selects `parse`, `compare`,
`satisfies`, `bump`, or `format`. A leading `v` is stripped from the input version on
`parse`, `compare`, `satisfies`, and `bump`. Build metadata is ignored for precedence
(spec §11).

### Compare two versions

`compare` returns `-1`, `0`, or `1` per the spec's major → minor → patch → prerelease
precedence rules. A prerelease version (`1.0.0-alpha`) sorts lower than the same release
(`1.0.0`); within a prerelease set, numeric identifiers sort numerically while
alphanumeric identifiers sort lexically.

```java
SemverOut cmp = ctx.runHelper("semver",
        new SemverIn("compare", null, "1.2.3-rc.1", "1.2.3", null, null,
                null, null, null, null, null))
        .as(SemverOut.class);
// cmp.result() == -1
```

### Gate a deployment with a version range

`satisfies` accepts exact (`"1.2.3"`), caret (`"^1.2.3"`), tilde (`"~1.2.3"`),
comparators (`">=1.2.3"`, `">1.2.3"`, `"<=1.2.3"`, `"<1.2.3"`), and partial wildcards
(`"1.x"`, `"1.2.x"`, `"1.2.*"`). Use it as a DSL-side gate before pushing a config
update or routing traffic to a new agent build.

```java
SemverOut ok = ctx.runHelper("semver",
        new SemverIn("satisfies", agentVersion, null, null, ">=1.2.0", null,
                null, null, null, null, null))
        .as(SemverOut.class);
if (Boolean.TRUE.equals(ok.result())) {
    // proceed
}
```

For `^0.2.3` the upper bound is the next minor (not the next major), per the spec's
"0.x.y is initial development" rule; `^1.2.3` locks the major.

### Bump and format

`bumpType` accepts `major`, `minor`, `patch`, and `preRelease`. `preRelease` increments
the last numeric identifier in the prerelease segment, or appends `.1` when no numeric
identifier exists. Bumping `preRelease` on a release version (no `-` segment) yields
`IllegalArgumentException`.

```java
SemverOut bumped = ctx.runHelper("semver",
        new SemverIn("bump", "1.2.3-rc.1", null, null, null, "preRelease",
                null, null, null, null, null))
        .as(SemverOut.class);
// bumped.result() == "1.2.3-rc.2"

SemverOut formatted = ctx.runHelper("semver",
        new SemverIn("format", null, null, null, null, null,
                1, 2, 3, "rc.1", "build.5"))
        .as(SemverOut.class);
// formatted.result() == "1.2.3-rc.1+build.5"
```

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

## Distributed tracing (OpenTelemetry)

`otel` exposes OpenTelemetry tracing to DSL authors through seven operations, selected by the
`mode` discriminator on `OtelIn` (matched case-insensitively):

- `"span"` — starts a span (`name` required, optional `attributes`) and returns its W3C
  traceparent string. That string is the handle for the span lifecycle operations below.
- `"endSpan"` — finalizes a span (`spanId` = the traceparent from `"span"`; optional `statusCode`
  `OK`/`ERROR`/`UNSET`, default `OK`, and `errorMessage`). Fail-fast: ending an already-ended or
  unknown span is an error, not a silent no-op.
- `"addEvent"` — attaches a named event (`eventName` required) with optional `attributes` to an
  open span.
- `"setBaggage"` / `"getBaggage"` — a local, JVM-scoped key/value store for business-key
  correlation within a single DSL run. This is *not* W3C baggage propagation; `getBaggage` on a
  key that was never set is an error.
- `"injectContext"` — injects W3C `traceparent` headers into a `headers` carrier map (returned
  as `OtelOut.result`). Useful before an `httpCall` whose headers should join the trace.
- `"extractContext"` — pulls the span-id out of inbound W3C trace headers; returns `""` when no
  valid traceparent is present.

`OtelOut.result` carries the per-mode payload: the traceparent for `"span"`, the baggage value for
`"getBaggage"`, the carrier `Map` for `"injectContext"`, the span-id string for
`"extractContext"`, and `Boolean.TRUE` for the no-payload modes.

### Bracket a business operation with a span

```java
String spanHandle = (String) ctx.runHelper("otel",
        new OtelIn("span", "place-order", Map.of("tenant", "acme"),
                null, null, null, null, null, null, null))
        .as(OtelOut.class).result();

try {
    // ... httpCall, listOps, etc. ...
    ctx.runHelper("otel", new OtelIn("addEvent", null, null,
            spanHandle, null, null, "order-validated", null, null, null))
            .as(OtelOut.class);
} catch (RuntimeException e) {
    ctx.runHelper("otel", new OtelIn("endSpan", null, null,
            spanHandle, "ERROR", e.getMessage(), null, null, null, null))
            .as(OtelOut.class);
    throw e;
}
ctx.runHelper("otel", new OtelIn("endSpan", null, null,
        spanHandle, "OK", null, null, null, null, null))
        .as(OtelOut.class);
```

### Propagate trace context into an outbound httpCall

```java
OtelOut injected = ctx.runHelper("otel",
        new OtelIn("injectContext", null, null, null, null, null,
                null, null, null, new HashMap<>()))
        .as(OtelOut.class);
Map<String, String> traceHeaders = (Map<String, String>) injected.result();
// merge traceHeaders into the HttpCallIn headers map
```

Two scope notes worth knowing. Each `"span"` call starts an independent top-level span — the
helper deliberately does not chain ambient OTel context across helper invocations, so
parent-child nesting between two helper-initiated spans is not supported. And when tracing is
disabled in the host application the `OpenTelemetry` bean is a no-op implementation: the helper
still succeeds, it just produces no exported spans — DSL code can call it unconditionally.

## Masking sensitive values

`mask` redacts sensitive values (card numbers, IBANs, emails, credentials) at the point of use,
so they can be safely interpolated into log lines, notification messages, or audit
`details_json`. It takes `MaskIn(value, mode, keepFirst, keepLast, maskChar, width)` and returns
`MaskOut(result)`.

Two modes, selected by `mode` (case-insensitive):

- `null` / `"edges"` — keep the configured edges visible and mask the middle.
- `"fixed"` — output is exactly `width` mask characters (default `8`), independent of the
  input length.

### Safe default (no `keepFirst`/`keepLast`)

When `mode` is `null` or `"edges"` and neither `keepFirst` nor `keepLast` is given, the exact
rule is: if the value has **8 or more Unicode code points**, the last 4 code points stay visible
and everything before them is masked (`"4111111111111111" → "************1111"`); if it is
shorter, the output is **exactly 8 mask characters** — a short value's true length never leaks
(`"abc" → "********"`).

```java
// Card number into a notification template:
MaskOut safeCard = ctx.runHelper("mask",
        new MaskIn(cardNumber, null, null, null, null, null))
        .as(MaskOut.class);
// safeCard.result() == "************1111"

// Explicit edges + custom mask char:
MaskOut ref = ctx.runHelper("mask",
        new MaskIn(payerReference, "edges", 2, 2, "#", null))
        .as(MaskOut.class);

// Fixed width, independent of input length:
MaskOut token = ctx.runHelper("mask",
        new MaskIn(apiToken, "fixed", null, null, null, 12))
        .as(MaskOut.class);
```

Conventions: a `null` `value` is rejected (`"mask.value is required"`, same as `hex`); an empty
value returns an empty result. `maskChar` defaults to `'*'`; when more than one character is
supplied, the first is used. Keeps are counted in Unicode code points (emoji / surrogate pairs
are never split), and negative `keepFirst`/`keepLast` are treated as `0`. If
`keepFirst + keepLast` reaches the value length, the keeps are clamped so exactly one code point
stays masked — the value is **never** returned unmasked. `width < 1` is rejected.
