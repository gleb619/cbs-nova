package cbs.nova.starter.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Cheap regression lock: ensure the console pattern keeps the MDC {@code rid} key. The pattern
 * either hardcodes it in {@code logback-spring.xml} or pulls it in via Spring Boot's
 * {@code LOG_LEVEL_PATTERN}, which is bound to {@code logging.pattern.level} in
 * {@code application.yml}. Without this, request-id correlation silently disappears and log triage
 * becomes painful.
 */
class LogbackPatternContractTest {

  @Test
  void logbackPatternIncludesRidMdcKey() throws IOException {
    String xml = resource("logback-spring.xml");
    String applicationYml = resource("application.yml");
    boolean ridRenderedDirectly = xml.contains("%X{rid");
    boolean ridViaLevelPattern = applicationYml.contains("%X{rid")
            && xml.contains("${LOG_LEVEL_PATTERN");
    assertTrue(ridRenderedDirectly || ridViaLevelPattern,
            "console pattern must render the rid MDC key, got logback-spring.xml:\n" + xml
                    + "\nand application.yml:\n" + applicationYml);
  }

  private static String resource(String name) throws IOException {
    try (InputStream in = LogbackPatternContractTest.class.getClassLoader()
            .getResourceAsStream(name)) {
      assertTrue(in != null, name + " must be present on the classpath");
      try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        return reader.lines().collect(Collectors.joining("\n"));
      }
    }
  }
}
