package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.starter.helper.model.Base64In;
import cbs.nova.starter.helper.model.Base64Out;
import java.nio.charset.StandardCharsets;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

class Base64HelperPropertyTest {
  private final Base64Helper helper = new Base64Helper();

  private String enc(String s, boolean u) {
    return helper
            .execute(SimpleContext.<Base64In>builder().body(new Base64In(s, "encode", u))
                    .mode(ExecutionMode.PREVIEW).build())
            .value()
            .result();
  }

  private String dec(String s, boolean u) {
    return helper
            .execute(SimpleContext.<Base64In>builder().body(new Base64In(s, "decode", u))
                    .mode(ExecutionMode.PREVIEW).build())
            .value()
            .result();
  }

  @Property(tries = 1000)
  void standardRoundTrip(@ForAll @StringLength(max = 200) String s) {
    assertThat(dec(enc(s, false), false)).isEqualTo(s);
  }

  @Property(tries = 1000)
  void urlSafeRoundTrip(@ForAll @StringLength(max = 200) String s) {
    assertThat(dec(enc(s, true), true)).isEqualTo(s);
  }

  @Property(tries = 1000)
  void standardEncodedLengthIsPadded(
          @ForAll @StringLength(max = 200) String s) {
    int n = s.getBytes(StandardCharsets.UTF_8).length;
    assertThat(enc(s, false).length()).isEqualTo(4 * ((n + 2) / 3));
  }

  @Property(tries = 1000)
  void standardEncodedCharset(@ForAll @StringLength(max = 200) String s) {
    assertThat(enc(s, false)).matches("^[A-Za-z0-9+/]*={0,2}$");
  }
}
