package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslRunEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class DslRunEncryption {

  private final FieldEncryptor encryptor;

  public void encrypt(DslRunEntity entity) {
    entity.setInputJson(encrypt(entity.getInputJson()));
    entity.setOutputJson(encrypt(entity.getOutputJson()));
    entity.setContextJson(encrypt(entity.getContextJson()));
  }

  public DslRunEntity decrypt(DslRunEntity entity) {
    entity.setInputJson(decrypt(entity.getInputJson()));
    entity.setOutputJson(decrypt(entity.getOutputJson()));
    entity.setContextJson(decrypt(entity.getContextJson()));
    return entity;
  }

  public @Nullable String encrypt(@Nullable String plain) {
    return encryptor.encrypt(plain);
  }

  public @Nullable String decrypt(@Nullable String cipher) {
    return encryptor.decrypt(cipher);
  }
}
