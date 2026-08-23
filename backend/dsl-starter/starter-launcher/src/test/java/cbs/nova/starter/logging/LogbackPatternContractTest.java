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
 * Cheap regression lock: ensure the bundled {@code logback-spring.xml} keeps the MDC {@code rid}
 * key in the console pattern. Without this, request-id correlation silently disappears and log
 * triage becomes painful.
 */
class LogbackPatternContractTest {

  @Test
  void logbackPatternIncludesRidMdcKey() throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
      assertTrue(in != null, "logback-spring.xml must be present on the classpath");
      String xml;
      try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        xml = reader.lines().collect(Collectors.joining("\n"));
      }
      assertTrue(xml.contains("%X{rid"),
          "logback-spring.xml must render the rid MDC key, got:\n" + xml);
    }
  }
}
