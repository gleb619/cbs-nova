import cbs.nova.dslexamples.ContentSanitizeModels.*;
import cbs.nova.starter.helper.model.CaseConvertIn;
import cbs.nova.starter.helper.model.CaseConvertOut;
import cbs.nova.starter.helper.model.FormatDateIn;
import cbs.nova.starter.helper.model.FormatDateOut;
import cbs.nova.starter.helper.model.GroupRecordsIn;
import cbs.nova.starter.helper.model.GroupRecordsOut;
import cbs.nova.starter.helper.model.HtmlEscapeIn;
import cbs.nova.starter.helper.model.HtmlEscapeOut;
import cbs.nova.starter.helper.model.UrlDecodeIn;
import cbs.nova.starter.helper.model.UrlDecodeOut;
import cbs.nova.starter.helper.model.XmlEscapeIn;
import cbs.nova.starter.helper.model.XmlEscapeOut;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


List<DslObject> define() {
  return Dsl.process("ContentSanitize")
      .input(ContentSanitizeIn.class)
      .output(ContentSanitizeOut.class)
      .description("Sanitizes inbound content for downstream rendering: urlDecodes percent-encoded "
          + "payload fields, htmlEscape / xmlEscape user-supplied text, caseConverts a title into a "
          + "kebab slug, formatDates a raw epoch-millis timestamp, and groupRecords the sanitized "
          + "items by a configurable key.")
      .execute(ctx -> {
        ContentSanitizeIn in = ctx.body();

        // 1) urlDecode: inbound title (form path of the post body -> readable string).
        var decodedTitle = ctx.runHelper("urlDecode",
            new UrlDecodeIn(in.encodedTitle(), "UTF-8", true));
        if (!decodedTitle.isSuccess()) {
          return Result.failure(decodedTitle.cause());
        }
        String title = decodedTitle.as(UrlDecodeOut.class).result();

        // 2) urlDecode: inbound body (form path; html-bearing text).
        var decodedBody = ctx.runHelper("urlDecode",
            new UrlDecodeIn(in.encodedBody(), "UTF-8", true));
        if (!decodedBody.isSuccess()) {
          return Result.failure(decodedBody.cause());
        }
        String body = decodedBody.as(UrlDecodeOut.class).result();

        // 3) urlDecode: inbound meta line (form path; xml-bearing text).
        var decodedMeta = ctx.runHelper("urlDecode",
            new UrlDecodeIn(in.encodedMeta(), "UTF-8", true));
        if (!decodedMeta.isSuccess()) {
          return Result.failure(decodedMeta.cause());
        }
        String meta = decodedMeta.as(UrlDecodeOut.class).result();

        // 4) htmlEscape: body may contain <, >, & etc.; make it safe for HTML insertion.
        var htmlSafe = ctx.runHelper("htmlEscape", new HtmlEscapeIn(body));
        if (!htmlSafe.isSuccess()) {
          return Result.failure(htmlSafe.cause());
        }
        String htmlSafeBody = htmlSafe.as(HtmlEscapeOut.class).result();

        // 5) xmlEscape: meta may contain <, >, & etc.; make it safe for XML insertion.
        var xmlSafe = ctx.runHelper("xmlEscape", new XmlEscapeIn(meta));
        if (!xmlSafe.isSuccess()) {
          return Result.failure(xmlSafe.cause());
        }
        String xmlSafeMeta = xmlSafe.as(XmlEscapeOut.class).result();

        // 6) caseConvert: turn the decoded title into a URL-friendly kebab slug.
        var slug = ctx.runHelper("caseConvert", new CaseConvertIn(title, "kebab"));
        if (!slug.isSuccess()) {
          return Result.failure(slug.cause());
        }
        String kebabSlug = slug.as(CaseConvertOut.class).result();

        // 7) formatDate: render the raw epoch-millis timestamp as an ISO-8601 UTC instant.
        var formatted = ctx.runHelper("formatDate",
            new FormatDateIn(Long.toString(in.rawTimestampMillis()), "ISO_INSTANT", "UTC"));
        if (!formatted.isSuccess()) {
          return Result.failure(formatted.cause());
        }
        String isoTimestamp = formatted.as(FormatDateOut.class).formatted();

        // 8) Build sanitized records that combine escaped payloads with the slug + ISO timestamp.
        //    These are the items the example feeds into groupRecords.
        List<Map<String, Object>> sanitized = List.of(
            new LinkedHashMap<>(Map.of(
                "title", title,
                "slug", kebabSlug,
                "htmlBody", htmlSafeBody,
                "xmlMeta", xmlSafeMeta,
                "isoTimestamp", isoTimestamp,
                in.groupByField(), "news")));

        var grouped = ctx.runHelper("groupRecords",
            new GroupRecordsIn(sanitized, in.groupByField()));
        if (!grouped.isSuccess()) {
          return Result.failure(grouped.cause());
        }
        Map<Object, List<Map<String, Object>>> groups = grouped.as(GroupRecordsOut.class).groups();

        return Result.success(new ContentSanitizeOut(
            title,
            body,
            meta,
            htmlSafeBody,
            xmlSafeMeta,
            kebabSlug,
            isoTimestamp,
            groups));
      })
      .compensation((ctx, history) ->
          ctx.log("ContentSanitize compensated: " + ctx.error().getMessage()))
      .buildList();
}