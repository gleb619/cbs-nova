package cbs.nova.starter.converter;

import cbs.nova.starter.model.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

public interface DslExceptionMapper {

  ResponseEntity<ErrorResponse> handle(Exception exception, WebRequest request);
}
