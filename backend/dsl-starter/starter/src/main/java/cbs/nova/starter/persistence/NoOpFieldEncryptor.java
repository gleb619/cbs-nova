package cbs.nova.starter.persistence;

import org.jspecify.annotations.Nullable;

/**
 * Default no-op encryptor used when encryption is not enabled.
 */
public class NoOpFieldEncryptor implements FieldEncryptor {

  @Override
  public @Nullable String encrypt(@Nullable String plain) {
    return plain;
  }

  @Override
  public @Nullable String decrypt(@Nullable String cipher) {
    return cipher;
  }
}
