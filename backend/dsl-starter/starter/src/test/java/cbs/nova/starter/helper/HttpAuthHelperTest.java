package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.HttpAuthIn;
import cbs.nova.starter.helper.model.HttpAuthOut;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpAuthHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final HttpAuthHelper helper = new HttpAuthHelper();

  // --- bearer ---------------------------------------------------------------

  @Test
  void bearerProducesAuthorizationHeader() {
    Map<String, String> headers = execute(in("bearer", "xyz", null, null, null, null, null, null))
            .value().headers();
    assertThat(headers).containsExactly(Map.entry("Authorization", "Bearer xyz"));
  }

  @Test
  void bearerEmptyTokenFails() {
    Result<HttpAuthOut> result = execute(in("bearer", "", null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bearerNullTokenFails() {
    Result<HttpAuthOut> result = execute(in("bearer", null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bearerPreservesInternalWhitespace() {
    Map<String, String> headers = execute(
            in("bearer", "  abc  ", null, null, null, null, null, null)).value().headers();
    assertThat(headers).containsExactly(Map.entry("Authorization", "Bearer   abc  "));
  }

  // --- basic ----------------------------------------------------------------

  @Test
  void basicKnownVectorAladdin() {
    Map<String, String> headers = execute(
            in("basic", null, "Aladdin", "open sesame", null, null, null, null))
            .value().headers();
    assertThat(headers)
            .containsExactly(Map.entry("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="));
  }

  @Test
  void basicEmptyUsernameFails() {
    Result<HttpAuthOut> result = execute(in("basic", null, "", "x", null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void basicNullUsernameFails() {
    Result<HttpAuthOut> result = execute(in("basic", null, null, "x", null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void basicEmptyPasswordIsAllowed() {
    // "user:" base64 = "dXNlcjo="
    Map<String, String> headers = execute(in("basic", null, "user", "", null, null, null, null))
            .value().headers();
    assertThat(headers).containsExactly(Map.entry("Authorization", "Basic dXNlcjo="));
  }

  // --- apiKey ---------------------------------------------------------------

  @Test
  void apiKeyDefaultHeaderNoPrefix() {
    Map<String, String> headers = execute(
            in("apiKey", null, null, null, "secret", null, null, null)).value().headers();
    assertThat(headers).containsExactly(Map.entry("X-Api-Key", "secret"));
  }

  @Test
  void apiKeyWithCustomHeaderAndPrefix() {
    Map<String, String> headers = execute(
            in("apiKey", null, null, null, "sk_live_xxx", "Authorization", "Bearer", null))
            .value().headers();
    assertThat(headers).containsExactly(Map.entry("Authorization", "Bearer sk_live_xxx"));
  }

  @Test
  void apiKeyEmptyPrefixHasNoLeadingSpace() {
    Map<String, String> headers = execute(in("apiKey", null, null, null, "k", null, "", null))
            .value().headers();
    assertThat(headers).containsExactly(Map.entry("X-Api-Key", "k"));
  }

  @Test
  void apiKeyEmptyKeyFails() {
    Result<HttpAuthOut> result = execute(in("apiKey", null, null, null, "", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void apiKeyNullKeyFails() {
    Result<HttpAuthOut> result = execute(in("apiKey", null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void apiKeyExplicitBlankHeaderFails() {
    Result<HttpAuthOut> result = execute(in("apiKey", null, null, null, "k", "", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- custom ---------------------------------------------------------------

  @Test
  void customProducesGivenHeaderAndValue() {
    Map<String, String> headers = execute(
            in("custom", null, null, null, null, "X-Auth-Token", null, "abc")).value().headers();
    assertThat(headers).containsExactly(Map.entry("X-Auth-Token", "abc"));
  }

  @Test
  void customEmptyHeaderFails() {
    Result<HttpAuthOut> result = execute(in("custom", null, null, null, null, "", null, "v"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customNullHeaderFails() {
    Result<HttpAuthOut> result = execute(in("custom", null, null, null, null, null, null, "v"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customNullValueFails() {
    Result<HttpAuthOut> result = execute(in("custom", null, null, null, null, "X", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customBlankValueIsAllowed() {
    Map<String, String> headers = execute(in("custom", null, null, null, null, "X", null, ""))
            .value().headers();
    assertThat(headers).containsExactly(Map.entry("X", ""));
  }

  // --- mode -----------------------------------------------------------------

  @Test
  void unknownModeFails() {
    Result<HttpAuthOut> result = execute(
            in("frobnicate", null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage(
                    "httpAuth.mode must be one of bearer, basic, apiKey, custom, was: frobnicate");
  }

  @Test
  void nullModeFails() {
    Result<HttpAuthOut> result = execute(in(null, null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("httpAuth.mode must be one of bearer, basic, apiKey, custom, was: null");
  }

  @Test
  void modeIsCaseInsensitive() {
    Map<String, String> headers = execute(in("BEARER", "abc", null, null, null, null, null, null))
            .value().headers();
    assertThat(headers).containsExactly(Map.entry("Authorization", "Bearer abc"));
  }

  // --- helpers --------------------------------------------------------------

  private static HttpAuthIn in(
          String mode,
          String token,
          String username,
          String password,
          String key,
          String header,
          String prefix,
          String value) {
    return new HttpAuthIn(mode, token, username, password, key, header, prefix, value);
  }

  private Result<HttpAuthOut> execute(HttpAuthIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
