package cbs.dsl.codegen;

import java.util.Map;

/**
 * Simple string template substitutor using {{key}} syntax.
 */
public class Substitutor {
  public static String format(String template, Map<String, String> params) {
    if (template == null || params == null) {
      return template;
    }
    StringBuilder result = new StringBuilder();
    int length = template.length();
    int i = 0;
    while (i < length) {
      int open = template.indexOf("{{", i);
      if (open == -1) {
        result.append(template, i, length);
        break;
      }
      int close = template.indexOf("}}", open + 2);
      if (close == -1) {
        result.append(template, i, length);
        break;
      }
      result.append(template, i, open);
      String key = template.substring(open + 2, close);
      String value = params.get(key);
      if (value != null) {
        result.append(value);
      } else {
        result.append("{{").append(key).append("}}");
      }
      i = close + 2;
    }
    return result.toString();
  }
}
