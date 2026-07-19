package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.ExternalCallTracker;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.List;

class DataSourceCallAutoConfigurationTest {

  private ExternalCallTracker tracker;
  private DataSourceProxyBeanPostProcessor postProcessor;
  private JdbcDataSource realDataSource;
  private DataSource wrappedDataSource;

  @BeforeEach
  void setUp() {
    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    tracker.stopTracking();

    realDataSource = new JdbcDataSource();
    realDataSource.setURL("jdbc:h2:mem:t146;DB_CLOSE_DELAY=-1");
    realDataSource.setUser("sa");
    realDataSource.setPassword("");

    postProcessor = new DataSourceProxyBeanPostProcessor(tracker);
    Object processed = postProcessor.postProcessAfterInitialization(realDataSource, "ds");
    assertThat(processed).isInstanceOf(DataSource.class);
    wrappedDataSource = (DataSource) processed;
  }

  @AfterEach
  void tearDown() {
    tracker.stopTracking();
    tracker.resetGlobalCounts();
  }

  @Test
  void capturesCreateInsertAndSelectAsDatabaseCalls() {
    JdbcTemplate jdbc = new JdbcTemplate(wrappedDataSource);

    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    tracker.startTracking(calls);
    assertThat(tracker.getActiveTracking()).isNotNull();

    jdbc.execute("CREATE TABLE t (id INT)");
    jdbc.update("INSERT INTO t VALUES (1)");
    Integer selected = jdbc.queryForObject("SELECT id FROM t", Integer.class);

    tracker.stopTracking();

    assertThat(selected).isEqualTo(1);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_DATABASE))
            .isGreaterThanOrEqualTo(1);

    assertThat(calls).isNotEmpty();
    assertThat(calls).allSatisfy(call -> {
      assertThat(call.type()).isEqualTo(ExternalCallTracker.TYPE_DATABASE);
      assertThat(call.target()).startsWith("jdbc:h2:mem:t146");
      assertThat(call.metadata()).containsKey("payload");
    });

    assertThat(calls).extracting(ExternalCallTracker.CallDetail::operation)
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
