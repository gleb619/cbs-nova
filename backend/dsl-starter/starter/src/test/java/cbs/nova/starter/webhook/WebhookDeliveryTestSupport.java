package cbs.nova.starter.webhook;

import cbs.nova.starter.persistence.CrudRepositories;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Builds a real {@link WebhookDeliveryRecordRepository} over a private in-memory H2 database (V3
 * migration applied), so handler and dispatcher tests can assert that delivery rows actually land.
 * The underlying Spring Data {@code WebhookDeliveryCrudRepository} proxy is built over the same
 * datasource, mirroring the application write path.
 */
public final class WebhookDeliveryTestSupport {

  private WebhookDeliveryTestSupport() {
  }

  public static WebhookDeliveryRecordRepository h2() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:webhook-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    try {
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V1__init.sql"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to set up in-memory dsl_webhook_deliveries table", e);
    }
    var repos = CrudRepositories.over(dataSource);
    return new WebhookDeliveryRecordRepository(repos.webhookDeliveries(), repos.dslQueries());
  }
}
