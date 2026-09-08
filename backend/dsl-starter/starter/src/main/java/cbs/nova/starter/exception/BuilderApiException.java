package cbs.nova.starter.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class BuilderApiException extends RuntimeException {

  private final HttpStatusCode statusCode;
  private final String code;

  public BuilderApiException(HttpStatusCode statusCode, String code, String message) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
  }

}
