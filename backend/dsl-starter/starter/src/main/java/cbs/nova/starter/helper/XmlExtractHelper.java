package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.XmlExtractIn;
import cbs.nova.starter.helper.model.XmlExtractOut;
import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.jspecify.annotations.NonNull;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Extracts a single value from an XML payload using an XPath 1.0 expression.
 *
 * <p>
 * The helper returns the text content of the first node matched by the expression, plus a
 * {@code present} flag. If the expression matches nothing, {@code value} is {@code null} and
 * {@code present} is {@code false}.
 *
 * <p>
 * XML parsing is hardened against XXE: {@code DOCTYPE} declarations, external general entities,
 * external parameter entities, and XInclude processing are all disabled.
 */
@Helper(name = "extractXml")
public class XmlExtractHelper implements Executable<XmlExtractIn, XmlExtractOut> {

  @Override
  public @NonNull Result<XmlExtractOut> execute(@NonNull Context<XmlExtractIn> ctx) {
    try {
      XmlExtractIn input = ctx.body();
      if (input.xml() == null || input.xml().isBlank()) {
        return Result.failure(new IllegalArgumentException("extractXml.xml is required"));
      }
      if (input.xpath() == null || input.xpath().isBlank()) {
        return Result.failure(new IllegalArgumentException("extractXml.xpath is required"));
      }

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

      DocumentBuilder builder = dbf.newDocumentBuilder();
      Node document = builder.parse(new InputSource(new StringReader(input.xml())));

      javax.xml.xpath.XPath xpath = XPathFactory.newInstance().newXPath();
      NodeList nodes = (NodeList) xpath.evaluate(input.xpath(), document, XPathConstants.NODESET);
      if (nodes.getLength() == 0) {
        return Result.success(new XmlExtractOut(null, false));
      }
      String text = nodes.item(0).getTextContent();
      return Result.success(new XmlExtractOut(text, true));
    } catch (SAXException | ParserConfigurationException | IOException e) {
      return Result.failure(
              new IllegalArgumentException("extractXml: invalid XML: " + e.getMessage(), e));
    } catch (XPathExpressionException e) {
      return Result.failure(
              new IllegalArgumentException("extractXml: invalid XPath: " + e.getMessage(), e));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }
}
