public class ContentSanitizeModels {

  /**
   * Inbound payload. {@code encodedTitle}, {@code encodedBody}, and {@code encodedMeta} arrive
   * percent-encoded from a URL/form path so the example can exercise {@code urlDecode} first.
   * {@code rawTimestampMillis} is the epoch-millis timestamp to render via {@code formatDate}.
   */
  public record ContentSanitizeIn(
      String encodedTitle,
      String encodedBody,
      String encodedMeta,
      long rawTimestampMillis,
      String groupByField) {
  }

  /**
   * Sanitized output. {@code decodedTitle}, {@code decodedBody}, and {@code decodedMeta} are the
   * urlDecode results. {@code htmlSafeBody} is the htmlEscape result, {@code xmlSafeMeta} the
   * xmlEscape result, {@code kebabSlug} the caseConvert (kebab) result, {@code isoTimestamp} the
   * formatDate result. {@code grouped} is the groupRecords result keyed on
   * {@code ContentSanitizeIn.groupByField()}.
   */
  public record ContentSanitizeOut(
      String decodedTitle,
      String decodedBody,
      String decodedMeta,
      String htmlSafeBody,
      String xmlSafeMeta,
      String kebabSlug,
      String isoTimestamp,
      java.util.Map<Object, java.util.List<java.util.Map<String, Object>>> grouped) {
  }
}