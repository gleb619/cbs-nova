package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.Sha256In;
import cbs.nova.starter.helper.model.Sha256Out;
import org.junit.jupiter.api.Test;

class Sha256HelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final Sha256Helper helper = new Sha256Helper();

  @Test
  void nistVectorAbcHex() {
    assertThat(digest("abc", "hex"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void emptyInputHex() {
    assertThat(digest("", "hex"))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  @Test
  void asciiDeterminism() {
    assertThat(digest("Hello, World!", "hex")).isEqualTo(digest("Hello, World!", "hex"));
  }

  @Test
  void nonAsciiUtf8Determinism() {
    String input = "héllo—世界";
    assertThat(digest(input, "hex")).isEqualTo(digest(input, "hex"));
  }

  @Test
  void abcBase64() {
    assertThat(digest("abc", "base64"))
            .isEqualTo("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=");
  }

  @Test
  void abcBase64urlAvoidsStandardAlphabetChars() {
    String encoded = digest("abc", "base64url");
    assertThat(encoded).doesNotContain("+", "/");
  }

  @Test
  void unknownEncodingFails() {
    Result<Sha256Out> result = execute(new Sha256In("abc", "octal"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("hex, base64, base64url");
  }

  @Test
  void nullInputFails() {
    Result<Sha256Out> result = execute(new Sha256In(null, "hex"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("sha256.input is required");
  }

  private String digest(String input, String encoding) {
    return execute(new Sha256In(input, encoding)).value().result();
  }

  private Result<Sha256Out> execute(Sha256In input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
