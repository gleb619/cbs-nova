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

## Adding a helper to this page

When a new helper ships, add a recipe to the section that matches its **use case**, not a new
per-helper section. A recipe is: one or two sentences of problem statement, the minimal call
snippet, and — if the helper is usually used in combination — one chained example.

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
