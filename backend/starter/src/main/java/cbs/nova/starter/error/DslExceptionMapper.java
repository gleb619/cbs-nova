package cbs.nova.starter.error;

import cbs.nova.starter.models.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/**
 * SPI for mapping exceptions thrown by cbs-nova controllers into {@link ErrorResponse} bodies. Host
 * applications can provide a custom bean to override the default {@link DefaultDslExceptionMapper}
 * behaviour.
 */
public interface DslExceptionMapper {

  ResponseEntity<ErrorResponse> handle(Exception exception, WebRequest request);
}
