package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.List;

class DataSourceCallConfigurationTest {
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();

  private RunIdKeyedExternalCallRecorder recorder;
  private DataSourceProxyBeanPostProcessor postProcessor;
  private JdbcDataSource realDataSource;
  private DataSource wrappedDataSource;

  @BeforeEach
  void setUp() {
    recorder = new RunIdKeyedExternalCallRecorder(dryRunLoggingContext, null);
    recorder.resetGlobalCounts();

    realDataSource = new JdbcDataSource();
    realDataSource.setURL("jdbc:h2:mem:t146;DB_CLOSE_DELAY=-1");
    realDataSource.setUser("sa");
    realDataSource.setPassword("");

    postProcessor = new DataSourceProxyBeanPostProcessor(recorder);
    Object processed = postProcessor.postProcessAfterInitialization(realDataSource, "ds");
    assertThat(processed).isInstanceOf(RecordingDataSource.class);
    wrappedDataSource = (DataSource) processed;
  }

  @AfterEach
  void tearDown() {
    recorder.resetGlobalCounts();
  }

  @Test
  void capturesCreateInsertAndSelectAsDatabaseCalls() {
    JdbcTemplate jdbc = new JdbcTemplate(wrappedDataSource);

    recorder.startRun("run-1");

    jdbc.execute("CREATE TABLE t (id INT)");
    jdbc.update("INSERT INTO t VALUES (1)");
    Integer selected = jdbc.queryForObject("SELECT id FROM t", Integer.class);

    List<ExternalCall> calls = recorder.finishRun("run-1");

    assertThat(selected).isEqualTo(1);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_DATABASE))
            .isGreaterThanOrEqualTo(1);

    assertThat(calls).isNotEmpty();
    assertThat(calls).allSatisfy(call -> {
      assertThat(call.type()).isEqualTo(ExternalCallRecorder.TYPE_DATABASE);
      assertThat(call.target()).startsWith("jdbc:h2:mem:t146");
      assertThat(call.metadata()).containsKey("payload");
    });

    assertThat(calls).extracting(ExternalCall::operation)
            .contains("CREATE", "INSERT", "SELECT");

    assertThat(calls).extracting(call -> call.metadata().get("payload"))
            .anyMatch(payload -> payload instanceof String s && s.contains("CREATE TABLE t"));
  }

  @Test
  void nonDataSourceBeansAreReturnedUnchanged() {
    Object plain = new Object();
    Object result = postProcessor.postProcessAfterInitialization(plain, "not-a-ds");
    assertThat(result).isSameAs(plain);
  }
}
