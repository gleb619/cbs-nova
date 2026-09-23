package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.XmlEscapeIn;
import cbs.nova.starter.helper.model.XmlEscapeOut;
import org.jspecify.annotations.NonNull;

/**
 * Escapes a string so it can be safely interpolated into XML text and attribute content.
 *
 * <p>
 * The five characters with markup meaning in XML are escaped per XML 1.0: {@code &} {@code <}
 * {@code >} {@code "} and {@code '} become {@code &amp;} {@code &lt;} {@code &gt;} {@code &quot;}
 * and {@code &apos;}. Input is required and must be non-empty.
 */
@Helper(name = "xmlEscape")
public class XmlEscapeHelper implements Executable<XmlEscapeIn, XmlEscapeOut> {

  @Override
  public @NonNull Result<XmlEscapeOut> execute(@NonNull Context<XmlEscapeIn> ctx) {
    try {
      XmlEscapeIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("xmlEscape.input is required"));
      }
      return Result.success(new XmlEscapeOut(escape(input.input())));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static String escape(String input) {
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        case '\'' -> sb.append("&apos;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
