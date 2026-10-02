package cbs.nova.starter.web;

import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Cheap, app-wide HTTP request body ceiling. Wired as a {@link HandlerFilterFunction} on every
 * functional router so that the cap is enforced before any controller reads the body.
 *
 * <p>
 * Two-pronged enforcement:
 *
 * <ol>
 * <li>If a {@code Content-Length} header is present and exceeds the configured limit, the request
 * is rejected immediately with {@link DslPayloadTooLargeException} (HTTP 413,
 * {@code PAYLOAD_TOO_LARGE}) — the body is never read.</li>
 * <li>If the header is missing (chunked transfer-encoding) or the client lies about the size, the
 * underlying {@link ServletInputStream} is wrapped in a counting/limiting stream that throws the
 * same exception as soon as a handler reads past the cap.</li>
 * </ol>
 *
 * <p>
 * Read-only methods ({@code GET}, {@code HEAD}, {@code OPTIONS}, {@code DELETE}) bypass the filter
 * since they do not carry one. {@code POST} / {@code PUT} / {@code PATCH} are in scope.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class RequestBodyLimitFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  private static final Set<HttpMethod> BODY_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT,
          HttpMethod.PATCH);

  /** Entity name used in {@code DslPayloadTooLargeException} for the global cap. */
  public static final String ENTITY_NAME = "request-body";

  private final CbsNovaWebProperties properties;

  @Override
  public ServerResponse filter(ServerRequest request,
          @SuppressWarnings("null") @Nullable HandlerFunction<ServerResponse> next)
          throws Exception {
    if (next == null) {
      // Should not happen — HandlerFilterFunction.andThen(...) never passes null, but the
      // framework declares the parameter as nullable, so guard defensively.
      throw new IllegalStateException("HandlerFilterFunction.next must not be null");
    }
    long limit = properties.maxRequestBytes();
    if (limit <= 0) {
      return next.handle(request);
    }
    if (!BODY_METHODS.contains(request.method())) {
      return next.handle(request);
    }
    long declared = request.headers().asHttpHeaders().getContentLength();
    if (declared > limit) {
      throw new DslPayloadTooLargeException(limit, declared, ENTITY_NAME);
    }
    // declared < 0 means Content-Length absent (chunked) or unknown. Always wrap defensively so a
    // client that lies about size still gets capped. declared == 0 with a POST is unusual but
    // harmless to wrap.
    HttpServletRequest servletRequest = request.servletRequest();
    ServletInputStream bounded = new CountingServletInputStream(servletRequest.getInputStream(),
            limit, ENTITY_NAME);
    HttpServletRequest wrapped = new WrappedServletRequest(servletRequest, bounded);
    ServerRequest boundedRequest = ServerRequest.create(wrapped, request.messageConverters());
    return next.handle(boundedRequest);
  }

  /**
   * {@link HttpServletRequestWrapper} that hands out a counting {@link ServletInputStream} instead
   * of the underlying one. Delegates everything else unchanged.
   */
  static final class WrappedServletRequest extends HttpServletRequestWrapper {

    private final ServletInputStream bounded;

    WrappedServletRequest(HttpServletRequest delegate, ServletInputStream bounded) {
      super(delegate);
      this.bounded = bounded;
    }

    @Override
    public ServletInputStream getInputStream() {
      return bounded;
    }

    @Override
    public java.io.BufferedReader getReader() {
      throw new UnsupportedOperationException("Reader not supported for bounded body");
    }
  }

  /**
   * {@link ServletInputStream} that counts bytes consumed and throws
   * {@link DslPayloadTooLargeException} once the configured cap is exceeded.
   */
  static final class CountingServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long limit;
    private final String entityName;
    private long read;
    private boolean finished;

    CountingServletInputStream(ServletInputStream delegate, long limit, String entityName) {
      this.delegate = delegate;
      this.limit = limit;
      this.entityName = entityName;
    }

    @Override
    public boolean isFinished() {
      return finished || delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }

    private int enforce(int n) {
      if (n <= 0) {
        return n;
      }
      long prospective = read + n;
      if (prospective > limit) {
        throw new DslPayloadTooLargeException(limit, prospective, entityName);
      }
      read = prospective;
      return n;
    }

    @Override
    public int read() throws IOException {
      if (finished) {
        return -1;
      }
      int b = delegate.read();
      if (b == -1) {
        finished = true;
        return -1;
      }
      enforce(1);
      return b;
    }

    @Override
    public int read(byte @Nullable [] b, int off, int len) throws IOException {
      if (finished) {
        return -1;
      }
      int n = delegate.read(b, off, len);
      if (n == -1) {
        finished = true;
        return -1;
      }
      enforce(n);
      return n;
    }

    @Override
    public int available() throws IOException {
      return delegate.available();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
      finished = true;
    }

    /**
     * Exposed for testing.
     */
    long bytesRead() {
      return read;
    }

    /**
     * Exposed for testing — returns the underlying stream's class for diagnostics.
     */
    @SuppressWarnings("unused")
    Class<? extends InputStream> delegateType() {
      return delegate.getClass();
    }
  }
}