package cbs.nova.model.exception;

public class SettingNotFoundException extends RuntimeException {

  public SettingNotFoundException(Long id) {
    super("Setting not found: id=" + id);
  }

  public SettingNotFoundException(String code) {
    super("Setting not found: code=" + code);
  }
}
