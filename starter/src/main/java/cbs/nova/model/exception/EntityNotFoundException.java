package cbs.nova.model.exception;

public class EntityNotFoundException extends RuntimeException {

  public EntityNotFoundException(String message) {
    super(message);
  }

  public EntityNotFoundException(String entity, Long id) {
    super("%s not found: id=%d".formatted(entity, id));
  }

  public EntityNotFoundException(String entity, String code) {
    super("%s not found: code=%s".formatted(entity, code));
  }
}
