package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.XmlExtractIn;
import cbs.nova.starter.helper.model.XmlExtractOut;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class XmlExtractHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final XmlExtractHelper helper = new XmlExtractHelper();

  @Test
  void singleElementExtract() {
    Result<XmlExtractOut> result = execute(
            new XmlExtractIn("<envelope><body>hello</body></envelope>", "/envelope/body/text()"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().value()).isEqualTo("hello");
    assertThat(result.value().present()).isTrue();
  }

  @Test
  void attributeExtract() {
    Result<XmlExtractOut> result = execute(
            new XmlExtractIn("<items><item id=\"42\">x</item></items>", "//item/@id"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().value()).isEqualTo("42");
    assertThat(result.value().present()).isTrue();
  }

  @Test
  void noMatchReturnsNullAndNotPresent() {
    Result<XmlExtractOut> result = execute(
            new XmlExtractIn("<envelope><body>hello</body></envelope>", "/envelope/missing"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().value()).isNull();
    assertThat(result.value().present()).isFalse();
  }

  @Test
  void malformedXmlFails() {
    Result<XmlExtractOut> result = execute(
            new XmlExtractIn("<envelope><body>hello</envelope>", "/envelope/body"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("extractXml: invalid XML");
    assertThat(result.cause()).hasCauseInstanceOf(SAXException.class);
  }

  @Test
  void invalidXPathFails() {
    Result<XmlExtractOut> result = execute(
            new XmlExtractIn("<envelope><body>hello</body></envelope>", "///"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("extractXml: invalid XPath");
  }

  @Test
  void emptyXmlFails() {
    Result<XmlExtractOut> result = execute(new XmlExtractIn("", "/x"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("extractXml.xml is required");
  }

  @Test
  void emptyXPathFails() {
    Result<XmlExtractOut> result = execute(new XmlExtractIn("<x/>", ""));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("extractXml.xpath is required");
  }

  @Test
  void xxeDoctypeIsRejected() {
    String payload = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo>&xxe;</foo>";
    Result<XmlExtractOut> result = execute(new XmlExtractIn(payload, "/foo"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isNotNull();
    if (result.isSuccess()) {
      assertThat(result.value().value()).doesNotContain("root");
    }
  }

  private Result<XmlExtractOut> execute(XmlExtractIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
