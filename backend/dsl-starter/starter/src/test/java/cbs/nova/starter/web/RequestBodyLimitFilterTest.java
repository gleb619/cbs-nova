package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.web.RequestBodyLimitFilter.CountingServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Unit coverage for {@link RequestBodyLimitFilter}. The filter is a
 * {@link org.springframework.web.servlet.function.HandlerFilterFunction}; tests build a real
 * {@link MockHttpServletRequest}, bridge it through {@link ServerRequest#create}, and assert
 * black-box behaviour.
 *
 * <p>
 * Coverage:
 * <ul>
 * <li>under-limit requests pass through to the handler unchanged;</li>
 * <li>requests whose declared {@code Content-Length} exceeds the limit are rejected with
 * {@link DslPayloadTooLargeException} <em>before</em> the handler runs;</li>
 * <li>chunked / no-{@code Content-Length} bodies are capped by the wrapping stream so the exception
 * fires when the handler actually reads past the limit;</li>
 * <li>a configured limit of {@code 0} disables the filter entirely;</li>
 * <li>{@code GET} / {@code DELETE} requests bypass the filter because they do not carry a
 * body.</li>
 * </ul>
 *
 * <p>
 * The shape of the resulting {@code 413 PAYLOAD_TOO_LARGE} response (HttpStatus.PAYLOAD_TOO_LARGE
 * and {@code code = "PAYLOAD_TOO_LARGE"}) is already covered by
 * {@code DslExceptionHandlerTest#payloadTooLargeExceptionMapsTo413WithPayloadTooLargeCode}.
 */
class RequestBodyLimitFilterTest {

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new ByteArrayHttpMessageConverter(),
                  new StringHttpMessageConverter());

  private static final CbsNovaWebProperties PROPS_1024 = new CbsNovaWebProperties(1024L);
  private static final CbsNovaWebProperties PROPS_DISABLED = new CbsNovaWebProperties(0L);

  // -----------------------------------------------------------------
  // Pass-through cases
  // -----------------------------------------------------------------

  @Test
  void underLimitPassesThroughToHandler() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = postJson("/api/dsl/drafts/x/save", new byte[256]);

    ServerResponse response = filter.filter(req, r -> ServerResponse.ok().body("passed"));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void getRequestBypassesFilter() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = get("/api/dsl/drafts");

    ServerResponse response = filter.filter(req, r -> ServerResponse.ok().body("passed"));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void deleteRequestBypassesFilter() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = delete("/api/dsl/drafts/x");

    ServerResponse response = filter.filter(req, r -> ServerResponse.ok().body("passed"));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  // -----------------------------------------------------------------
  // Content-Length > limit
  // -----------------------------------------------------------------

  @Test
  void contentLengthOverLimitThrowsBeforeHandlerRuns() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = postJson("/api/dsl/drafts/x/save", new byte[4096]);

    assertThatThrownBy(() -> filter.filter(req, r -> {
      throw new AssertionError("handler must not run when Content-Length exceeds limit");
    })).isInstanceOfSatisfying(DslPayloadTooLargeException.class, ex -> {
      assertThat(ex.getLimit()).isEqualTo(1024L);
      assertThat(ex.getEntityName()).isEqualTo(RequestBodyLimitFilter.ENTITY_NAME);
    });
  }

  @Test
  void contentLengthEqualToLimitPasses() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = postJson("/api/dsl/drafts/x/save", new byte[1024]);

    ServerResponse response = filter.filter(req, r -> ServerResponse.ok().body("ok"));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  // -----------------------------------------------------------------
  // Chunked / no Content-Length
  // -----------------------------------------------------------------

  @Test
  void chunkedBodyOverLimitThrowsWhenHandlerReads() throws Exception {
    // No Content-Length → must fall back to the counting stream. The filter wires it on every
    // body-bearing request, so the wrapped stream must throw as soon as the handler reads
    // past the cap.
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = chunkedPost("/api/dsl/drafts/x/save", new byte[2048]);

    assertThatThrownBy(() -> filter.filter(req, r -> {
      InputStream body;
      try {
        body = r.servletRequest().getInputStream();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      byte[] sink = new byte[256];
      try {
        while (body.read(sink) != -1) {
          // drain — must throw once past the limit
        }
      } catch (DslPayloadTooLargeException ex) {
        throw ex;
      } catch (IOException ex) {
        throw new RuntimeException("expected DslPayloadTooLargeException, got " + ex, ex);
      }
      return ServerResponse.ok().body("should not reach");
    })).isInstanceOf(DslPayloadTooLargeException.class);
  }

  @Test
  void chunkedBodyUnderLimitSucceeds() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = chunkedPost("/api/dsl/drafts/x/save", new byte[512]);

    ServerResponse response = filter.filter(req, r -> {
      try {
        // Drain the wrapped stream so any counting bugs would surface
        r.servletRequest().getInputStream().readAllBytes();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      return ServerResponse.ok().body("ok");
    });

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  // -----------------------------------------------------------------
  // Disabled path
  // -----------------------------------------------------------------

  @Test
  void limitZeroDisablesFilterEvenForLargeBody() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_DISABLED);
    ServerRequest req = postJson("/api/dsl/drafts/x/save", new byte[8192]);

    ServerResponse response = filter.filter(req, r -> ServerResponse.ok().body("passed"));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void propertiesNegativeValueTreatedAsDisabled() throws Exception {
    CbsNovaWebProperties props = new CbsNovaWebProperties(-42L);
    assertThat(props.maxRequestBytes()).isZero();
  }

  // -----------------------------------------------------------------
  // Defensive null guard
  // -----------------------------------------------------------------

  @Test
  void nullNextThrowsIllegalState() throws Exception {
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(PROPS_1024);
    ServerRequest req = postJson("/api/dsl/drafts/x/save", new byte[8]);

    assertThatThrownBy(() -> filter.filter(req, null))
            .isInstanceOf(IllegalStateException.class);
  }

  // -----------------------------------------------------------------
  // CountingServletInputStream — driven directly via a real MockServletInputStream so the
  // wrapping contract (count bytes, throw on overage) is observable through the InputStream
  // the controller would actually read.
  // -----------------------------------------------------------------

  @Nested
  class CountingStream {

    @Test
    void readByteCountsOneAndAcceptsBelowLimit() throws Exception {
      CountingServletInputStream stream = stream(new byte[4], 8);
      assertThat(stream.read()).isNotEqualTo(-1);
      assertThat(stream.read()).isNotEqualTo(-1);
      assertThat(stream.read()).isNotEqualTo(-1);
      assertThat(stream.read()).isNotEqualTo(-1);
      assertThat(stream.read()).isEqualTo(-1);
    }

    @Test
    void readByteOverLimitThrows() throws Exception {
      CountingServletInputStream stream = stream(new byte[8], 3);
      assertThat(stream.read()).isNotEqualTo(-1);
      assertThat(stream.read()).isNotEqualTo(-1);
      assertThat(stream.read()).isNotEqualTo(-1);
      // 4th byte would cross the cap
      assertThatThrownBy(stream::read).isInstanceOf(DslPayloadTooLargeException.class);
    }

    @Test
    void bulkReadStopsAtBoundary() throws Exception {
      CountingServletInputStream stream = stream(new byte[16], 8);
      byte[] buf = new byte[8];
      int n = stream.read(buf, 0, 8);
      assertThat(n).isEqualTo(8);
      assertThatThrownBy(() -> stream.read(buf, 0, 8))
              .isInstanceOf(DslPayloadTooLargeException.class);
    }

    @Test
    void zeroLengthReadReturnsZero() throws Exception {
      CountingServletInputStream stream = stream(new byte[8], 4);
      byte[] buf = new byte[8];
      assertThat(stream.read(buf, 0, 0)).isZero();
    }

    @Test
    void eofReturnsMinusOneWithoutThrow() throws Exception {
      CountingServletInputStream stream = stream(new byte[2], 100);
      byte[] buf = new byte[8];
      stream.read(buf, 0, 8);
      assertThat(stream.read(buf, 0, 8)).isEqualTo(-1);
    }

    @Test
    void closeMarksFinished() throws Exception {
      CountingServletInputStream stream = stream(new byte[4], 8);
      stream.close();
      assertThat(stream.isFinished()).isTrue();
      assertThat(stream.read()).isEqualTo(-1);
    }

    private RequestBodyLimitFilter.CountingServletInputStream stream(byte[] src, long limit)
            throws Exception {
      ServletInputStream delegate = new ServletInputStream() {
        private final InputStream srcStream = new ByteArrayInputStream(src);

        @Override
        public boolean isFinished() {
          try {
            return srcStream.available() == 0;
          } catch (IOException e) {
            return true;
          }
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          // no-op for tests
        }

        @Override
        public int read() throws IOException {
          return srcStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          return srcStream.read(b, off, len);
        }
      };
      Constructor<RequestBodyLimitFilter.CountingServletInputStream> ctor = RequestBodyLimitFilter.CountingServletInputStream.class
              .getDeclaredConstructor(
                      ServletInputStream.class, long.class, String.class);
      ctor.setAccessible(true);
      return ctor.newInstance(delegate, limit, RequestBodyLimitFilter.ENTITY_NAME);
    }
  }

  // -----------------------------------------------------------------
  // MockHttpServletRequest builders — Mirror the canonical pattern used in
  // DslDefinitionBundleResourceTest so the filter sees the real `getInputStream()` surface.
  // -----------------------------------------------------------------

  private static ServerRequest get(String path) {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest delete(String path) {
    MockHttpServletRequest req = new MockHttpServletRequest("DELETE", path);
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest postJson(String path, byte[] body) {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
    req.setContentType("application/json");
    // MockHttpServletRequest derives Content-Length from the body bytes; no explicit header.
    req.setContent(body);
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest chunkedPost(String path, byte[] body) {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
    req.setContentType("application/json");
    req.setContent(body);
    // Mark the request as chunked: drop Content-Length, set Transfer-Encoding: chunked.
    HttpHeaders headers = new HttpHeaders();
    for (String name : List.of("Content-Type", "Transfer-Encoding")) {
      headers.add(name, req.getHeader(name));
    }
    // Use setContent + manual content-length -1 to mimic a chunked uploader. Spring's
    // MockHttpServletRequest exposes -1 from getContentLengthLong when set so.
    req.removeHeader("Content-Length");
    req.addHeader("Transfer-Encoding", "chunked");
    // Ensure the wrapped request reports -1 length
    try {
      java.lang.reflect.Field f = MockHttpServletRequest.class
              .getDeclaredField("contentLength");
      f.setAccessible(true);
      f.setInt(req, -1);
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
      // MockHttpServletRequest#getContentLengthLong() returns -1 by default if no body has
      // been set; with setContent(body) it returns body.length. The two read paths differ,
      // but the filter consults request.headers().asHttpHeaders().getContentLength() which
      // delegates to the headers map. After removeHeader above that returns -1, triggering
      // the chunked branch in the filter.
    }
    return ServerRequest.create(req, CONVERTERS);
  }

  // Helper: ASCII byte filler.
  @SuppressWarnings("unused")
  private static byte[] ascii(int n) {
    byte[] buf = new byte[n];
    for (int i = 0; i < n; i++) {
      buf[i] = (byte) ('a' + (i % 26));
    }
    return buf;
  }

  @SuppressWarnings("unused")
  private static String asString(byte[] b) {
    return new String(b, StandardCharsets.UTF_8);
  }
}
