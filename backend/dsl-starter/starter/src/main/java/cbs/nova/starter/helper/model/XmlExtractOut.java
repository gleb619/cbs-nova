package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code extractXml} helper.
 *
 * <p>
 * {@code value} is the text content of the first matched node, or {@code null} when no node
 * matched. {@code present} is {@code true} when a node was matched.
 */
public record XmlExtractOut(String value, boolean present) {
}
