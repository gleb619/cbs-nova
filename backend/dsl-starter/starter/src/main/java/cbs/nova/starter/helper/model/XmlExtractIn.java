package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code extractXml} helper.
 *
 * <p>
 * {@code xml} is the XML payload to parse. {@code xpath} is the XPath 1.0 expression used to select
 * the first matching node.
 */
public record XmlExtractIn(String xml, String xpath) {
}
