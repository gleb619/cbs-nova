package cbs.nova.starter.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public class BuilderApiException extends RuntimeException {

  private final HttpStatusCode statusCode;
  private final String code;
  private final String message;

  @Override
  public String getMessage() {
    return message;
  }
}
