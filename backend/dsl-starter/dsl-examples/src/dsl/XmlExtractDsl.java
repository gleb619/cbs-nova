import cbs.nova.dslexamples.XmlExtractModels.*;
import cbs.nova.starter.helper.model.XmlExtractIn;
import cbs.nova.starter.helper.model.XmlExtractOut;

List<DslObject> define() {
  return Dsl.process("XmlExtract")
      .input(ExtractIn.class)
      .output(ExtractOut.class)
      .execute(ctx -> {
        ExtractIn in = ctx.body();

        // Small representative payload: a SOAP-ish envelope with a body and
        // an attribute on a list item. XPath 1.0 only.
        String xml = "<envelope>"
            + "  <body>hello</body>"
            + "  <items>"
            + "    <item id=\"42\">first</item>"
            + "    <item id=\"7\">second</item>"
            + "  </items>"
            + "</envelope>";

        // Path 1: a guaranteed-to-match xpath -> value present, present=true.
        var primary = ctx.runHelper("extractXml",
            new XmlExtractIn(xml, in.primaryXpath()));
        if (!primary.isSuccess()) {
          return Result.failure(primary.cause());
        }
        XmlExtractOut primaryOut = primary.as(XmlExtractOut.class);

        // Path 2: a not-expected-to-match xpath -> value null, present=false.
        var optional = ctx.runHelper("extractXml",
            new XmlExtractIn(xml, in.optionalXpath()));
        if (!optional.isSuccess()) {
          return Result.failure(optional.cause());
        }
        XmlExtractOut optionalOut = optional.as(XmlExtractOut.class);

        return Result.success(new ExtractOut(
            primaryOut.value(),
            primaryOut.present(),
            optionalOut.value(),
            optionalOut.present()));
      })
      .buildList();
}