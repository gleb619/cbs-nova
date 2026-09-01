package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

class ExecutionCsvWriterTest {

  private static final Instant STARTED_AT = Instant.parse("2026-08-13T10:00:00Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-08-13T10:00:05Z");

  @Test
  void headerRowIsFirstLine() {
    String csv = write(List.of(run().build()));

    assertThat(csv).startsWith(
            "runId,processName,status,mode,triggeredBy,correlationId,startedAt,finishedAt,"
                    + "duration,error,input,output\r\n");
  }

  @Test
  void commaInFieldIsQuoted() {
    DslRun run = run().input("a,b,c").output("plain").build();

    String csv = write(List.of(run));

    assertThat(csv).contains(",\"a,b,c\",plain\r\n");
  }

  @Test
  void embeddedDoubleQuoteIsEscaped() {
    DslRun run = run().input("she said \"hello\"").output("plain").build();

    String csv = write(List.of(run));

    assertThat(csv).contains(",\"she said \"\"hello\"\"\",plain\r\n");
  }

  @Test
  void carriageReturnAndLineFeedInFieldAreQuoted() {
    DslRun run = run().input("line1\r\nline2").output("plain").build();

    String csv = write(List.of(run));

    assertThat(csv).contains(",\"line1\r\nline2\",plain\r\n");
  }

  @Test
  void unicodeCharactersArePreserved() {
    DslRun run = run()
            .processName("Кредит")
            .triggeredBy(null)
            .correlationId(null)
            .input("€100")
            .build();

    String csv = write(List.of(run));

    assertThat(csv)
            .contains(",Кредит,COMPLETED,RUN,,,2026-08-13T10:00:00Z,2026-08-13T10:00:05Z,5,,€100,");
  }

  @Test
  void nullFieldsAreExportedEmpty() {
    DslRun run = DslRun.builder()
            .runId("run-nulls")
            .processName("Alpha")
            .status("RUNNING")
            .startedAt(STARTED_AT)
            .finishedAt(null)
            .executionMode(null)
            .triggeredBy(null)
            .correlationId(null)
            .input(null)
            .output(null)
            .error(null)
            .build();

    String csv = write(List.of(run));

    assertThat(csv).contains(",RUNNING,RUN,,,2026-08-13T10:00:00Z,,,,,\r\n");
  }

  @Test
  void inputAndOutputAreTruncatedToBudget() {
    DslRun run = run().input("1234567890").output("abcdefghij").build();

    String csv = write(List.of(run), new ExecutionCsvWriter.Config(5));

    assertThat(csv).contains(",12345,abcde\r\n");
  }

  @Test
  void durationIsCalculatedInSeconds() {
    DslRun run = run().startedAt(STARTED_AT).finishedAt(FINISHED_AT).build();

    String csv = write(List.of(run));

    assertThat(csv).contains(",2026-08-13T10:00:00Z,2026-08-13T10:00:05Z,5,");
  }

  @Test
  void modeDefaultsToRunWhenNullOrBlank() {
    DslRun nullMode = run().executionMode(null).runId("null-mode").build();
    DslRun blankMode = run().executionMode("   ").runId("blank-mode").build();

    String csv = write(List.of(nullMode, blankMode));

    assertThat(csv).contains("null-mode,Alpha,COMPLETED,RUN");
    assertThat(csv).contains("blank-mode,Alpha,COMPLETED,RUN");
  }

  @Test
  void modeIsUpperCasedWhenPresent() {
    DslRun run = run().executionMode("preview").runId("lower-mode").build();

    String csv = write(List.of(run));

    assertThat(csv).contains("lower-mode,Alpha,COMPLETED,PREVIEW");
  }

  @Test
  void errorIsExportedAsFirstLineOnly() {
    DslRun run = run().error("first line\r\nsecond line\nthird").build();

    String csv = write(List.of(run));

    assertThat(csv).contains("\"first line");
    assertThat(csv).doesNotContain("second line");
  }

  private static String write(List<DslRun> runs) {
    return write(runs, ExecutionCsvWriter.Config.defaults());
  }

  private static String write(List<DslRun> runs, ExecutionCsvWriter.Config config) {
    StringWriter writer = new StringWriter();
    try {
      new ExecutionCsvWriter().writeCsv(runs, writer, config);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return writer.toString();
  }

  private static DslRun.DslRunBuilder run() {
    return DslRun.builder()
            .runId("run-1")
            .processName("Alpha")
            .status("COMPLETED")
            .startedAt(STARTED_AT)
            .finishedAt(FINISHED_AT)
            .executionMode("RUN")
            .triggeredBy("operator")
            .correlationId("corr-1")
            .input("{}")
            .output("{\"ok\":true}");
  }
}
