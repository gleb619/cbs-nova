package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import cbs.nova.starter.persistence.CrudRepositories;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleFiringSearchResult;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Builds a real {@link NotificationRuleFiringRepository} over a private in-memory H2 database (V1
 * migration applied), so handler and engine tests can assert against the firing log without going
 * through the full Spring Boot context.
 */
public final class NotificationRuleFiringTestSupport {

  private NotificationRuleFiringTestSupport() {
  }

  /**
   * Test harness exposing both the write and the paged-search surface, so integration tests can
   * assert against {@link NotificationRuleFiringRepository#purgeOlderThan} plus the round-tripped
   * rows.
   */
  public record H2Repository(NotificationRuleFiringRepository repository) {

    public long insert(NotificationRuleFiringEntity row) {
      return repository.insert(row);
    }

    public NotificationRuleFiringSearchResult search(Long ruleId, int offset, int limit) {
      return repository.search(ruleId, offset, limit);
    }
  }

  public static NotificationRuleFiringRepository h2() {
    return h2WithSearch().repository();
  }

  public static H2Repository h2WithSearch() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:firing-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    try {
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V1__init.sql"));
    } catch (Exception e) {
      throw new IllegalStateException(
              "failed to set up in-memory dsl_notification_rule_firing table",
              e);
    }
    var repos = CrudRepositories.over(dataSource);
    return new H2Repository(new NotificationRuleFiringRepository(repos.notificationRuleFirings(),
            repos.dslQueries()));
  }
}
