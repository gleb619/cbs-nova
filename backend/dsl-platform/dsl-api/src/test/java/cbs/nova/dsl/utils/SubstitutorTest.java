package cbs.nova.dsl.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Map;

class SubstitutorTest {

  @Test
  void passthroughReturnsTemplateUnchanged() {
    assertThat(Substitutor.on("hello world").render()).isEqualTo("hello world");
  }

  @Test
  void basicSingleSubstitution() {
    assertThat(Substitutor.on("hello ${name}").with("name", "Alice").render())
            .isEqualTo("hello Alice");
  }

  @Test
  void basicMultipleDistinctSubstitutions() {
    assertThat(Substitutor.on("${greeting} ${name}!")
            .with("greeting", "Hello")
            .with("name", "Bob")
            .render())
            .isEqualTo("Hello Bob!");
  }

  @Test
  void repeatedKeyReplacedEverywhere() {
    assertThat(Substitutor.on("${x} and ${x} again")
            .with("x", "same")
            .render())
            .isEqualTo("same and same again");
  }

  @Test
  void adjacentPlaceholdersBothResolve() {
    assertThat(Substitutor.on("${a}${b}")
            .with("a", "1")
            .with("b", "2")
            .render())
            .isEqualTo("12");
  }

  @Test
  void dollarSignValueRoundTripsVerbatim() {
    assertThat(Substitutor.on("price=${p}")
            .with("p", "$5.00")
            .render())
            .isEqualTo("price=$5.00");
  }

  @Test
  void backslashValueRoundTripsVerbatim() {
    assertThat(Substitutor.on("path=${p}")
            .with("p", "C:\\path")
            .render())
            .isEqualTo("path=C:\\path");
  }

  @Test
  void nullValueCoercesToEmptyString() {
    assertThat(Substitutor.on("before${k}after")
            .with("k", null)
            .render())
            .isEqualTo("beforeafter");
  }

  @Test
  void missingPlaceholderThrowsIllegalArgumentExceptionWithKey() {
    assertThatThrownBy(() -> Substitutor.on("hello ${missing}").render())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing");
  }

  @Test
  void missingHandlerProvidesReplacementValue() {
    assertThat(Substitutor.on("hello ${missing}")
            .missing(key -> "[" + key + "]")
            .render())
            .isEqualTo("hello [missing]");
  }

  @Test
  void missingHandlerReceivesExactKey() {
    var capturedKey = new String[1];
    Substitutor.on("${myKey}")
            .missing(key -> {
              capturedKey[0] = key;
              return "";
            })
            .render();
    assertThat(capturedKey[0]).isEqualTo("myKey");
  }

  @Test
  void emptyBracesPassThroughUnchanged() {
    assertThat(Substitutor.on("literal ${} here").render())
            .isEqualTo("literal ${} here");
  }

  @Test
  void emptyBracesDoNotInvokeMissingHandler() {
    assertThat(Substitutor.on("literal ${} here")
            .missing(key -> "HANDLED")
            .render())
            .isEqualTo("literal ${} here");
  }

  @Test
  void formatMethodDelegatesToBuilder() {
    assertThat(Substitutor.format("${a} ${b}", Map.of("a", "one", "b", "two")))
            .isEqualTo("one two");
  }

  @Test
  void builderFluencyCombinesScalarMapAndMissingHandler() {
    var map = Map.of("b", "B");
    assertThat(Substitutor.on("${a}${b}${c}")
            .with("a", "A")
            .with(map)
            .missing(key -> "?" + key + "?")
            .render())
            .isEqualTo("AB?c?");
  }

  @Test
  void mapWithValuesMergesAndDoesNotReplaceExistingEntries() {
    var map = Map.of("a", "fromMap");
    assertThat(Substitutor.on("${a}")
            .with("a", "fromScalar")
            .with(map)
            .render())
            .isEqualTo("fromMap");
  }

  @Test
  void missingHandlerReturnValueIsQuoteReplacementEscaped() {
    assertThat(Substitutor.on("${x}")
            .missing(key -> "$" + key + "\\")
            .render())
            .isEqualTo("$x\\");
  }
}
