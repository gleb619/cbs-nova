package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.HtmlEscapeIn;
import cbs.nova.starter.helper.model.HtmlEscapeOut;
import org.jspecify.annotations.NonNull;

/**
 * Escapes a string so it can be safely interpolated into HTML text and attribute content.
 *
 * <p>
 * The five characters with markup meaning in HTML are escaped per HTML5: {@code &} {@code <}
 * {@code >} {@code "} and {@code '} become {@code &amp;} {@code &lt;} {@code &gt;} {@code &quot;}
 * and {@code &#39;}. Input is required and must be non-empty.
 */
@Helper(name = "htmlEscape")
public class HtmlEscapeHelper implements Executable<HtmlEscapeIn, HtmlEscapeOut> {

  @Override
  public @NonNull Result<HtmlEscapeOut> execute(@NonNull Context<HtmlEscapeIn> ctx) {
    try {
      HtmlEscapeIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("htmlEscape.input is required"));
      }
      return Result.success(new HtmlEscapeOut(escape(input.input())));
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
        case '\'' -> sb.append("&#39;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
