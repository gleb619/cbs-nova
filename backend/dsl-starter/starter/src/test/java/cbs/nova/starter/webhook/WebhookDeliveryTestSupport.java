package cbs.nova.starter.webhook;

import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Builds a real {@link WebhookDeliveryRecordRepository} over a private in-memory H2 database (V3
 * migration applied), so handler and dispatcher tests can assert that delivery rows actually land.
 */
public final class WebhookDeliveryTestSupport {

  private WebhookDeliveryTestSupport() {
  }

  public static WebhookDeliveryRecordRepository h2() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:webhook-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    try {
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V1__init.sql"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to set up in-memory dsl_webhook_deliveries table", e);
    }
    return new WebhookDeliveryRecordRepository(new NamedParameterJdbcTemplate(dataSource));
  }
}
