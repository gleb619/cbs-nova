package cbs.nova.starter.persistence;

import org.jspecify.annotations.Nullable;

/**
 * Application-level encryptor for sensitive stored fields.
 */
public interface FieldEncryptor {

  @Nullable
  String encrypt(@Nullable String plain);

  @Nullable
  String decrypt(@Nullable String cipher);
}
