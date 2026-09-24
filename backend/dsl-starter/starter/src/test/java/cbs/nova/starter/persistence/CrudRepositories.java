package cbs.nova.starter.persistence;

import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds real Spring Data JDBC repository proxies over an externally supplied {@link DataSource}
 * (private in-memory H2 in unit tests, Postgres in the deprecated event integration test), so
 * repository-level round-trips exercise the same write path the application uses.
 *
 * <p>
 * Mirrors the {@code @EnableJdbcRepositories} scan of {@code DslRunRepositoryConfiguration} but
 * registers only the CRUD repositories needed here (avoiding the {@code dsl_runs} repositories,
 * which need the custom {@code DslRunNamingStrategy} and are irrelevant to these tests).
 */
public final class CrudRepositories {

  private CrudRepositories() {
  }

  public record Repositories(
          ApiKeyCrudRepository apiKeys,
          DslEventCrudRepository dslEvents,
          WebhookDeliveryCrudRepository webhookDeliveries,
          DslDefinitionTestCrudRepository definitionTests,
          NotificationRuleCrudRepository notificationRules,
          NotificationRuleFiringCrudRepository notificationRuleFirings,
          ChangeRequestCrudRepository changeRequests,
          CompileDiagnosticCrudRepository compileDiagnostics,
          ExtendedSelectQueryExecutor dslQueries) {
  }

  public static Repositories over(DataSource dataSource) {
    var context = new AnnotationConfigApplicationContext();
    context.registerBean("dataSource", DataSource.class, () -> dataSource);
    context.registerBean("transactionManager", PlatformTransactionManager.class,
            () -> new DataSourceTransactionManager(dataSource));
    context.registerBean(NamedParameterJdbcOperations.class,
            () -> new NamedParameterJdbcTemplate(dataSource));
    context.register(CrudRepositoryConfig.class);
    context.refresh();
    return new Repositories(
            context.getBean(ApiKeyCrudRepository.class),
            context.getBean(DslEventCrudRepository.class),
            context.getBean(WebhookDeliveryCrudRepository.class),
            context.getBean(DslDefinitionTestCrudRepository.class),
            context.getBean(NotificationRuleCrudRepository.class),
            context.getBean(NotificationRuleFiringCrudRepository.class),
            context.getBean(ChangeRequestCrudRepository.class),
            context.getBean(CompileDiagnosticCrudRepository.class),
            new ExtendedSelectQueryExecutor(new NamedParameterJdbcTemplate(dataSource)));
  }

  @Configuration
  @EnableJdbcRepositories(basePackageClasses = CrudRepositoryConfig.class, considerNestedRepositories = false, includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
      ApiKeyCrudRepository.class,
      DslEventCrudRepository.class,
      WebhookDeliveryCrudRepository.class,
      DslDefinitionTestCrudRepository.class,
      NotificationRuleCrudRepository.class,
      NotificationRuleFiringCrudRepository.class,
      ChangeRequestCrudRepository.class,
      CompileDiagnosticCrudRepository.class
  }))
  static class CrudRepositoryConfig extends AbstractJdbcConfiguration {
  }
}
