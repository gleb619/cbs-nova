package cbs.nova.starter;

import cbs.nova.starter.converter.DslAuditMapperImpl;
import cbs.nova.starter.model.DslAudit;
import cbs.nova.starter.persistence.DslAuditCrudRepository;
import cbs.nova.starter.persistence.DslAuditStore;
import cbs.nova.starter.service.DslAuditService;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

public final class AuditTestSupport {

  private AuditTestSupport() {
  }

  /**
   * T880: keeps the most recent {@link #h2()} context on a thread-local so chain-tamper tests can
   * grab the same {@link DataSource} to issue raw JDBC.
   */
  private static final ThreadLocal<AnnotationConfigApplicationContext> LAST_CONTEXT = new ThreadLocal<>();

  /** Returns the context created by the most recent {@link #h2()} call on this thread. */
  public static AnnotationConfigApplicationContext lastContext() {
    AnnotationConfigApplicationContext ctx = LAST_CONTEXT.get();
    if (ctx == null) {
      throw new IllegalStateException(
              "no AuditTestSupport.h2() context on this thread — call h2() first");
    }
    return ctx;
  }

  public record Harness(DslAuditService service, DslAuditStore store) {

    public void append(DslAudit row) {
      store.append(row);
    }
  }

  public static Harness h2() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:audit-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
    dataSource.setUser("sa");
    try {
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V1__init.sql"));
      // T880: chain columns + unique index (idempotent for tests that re-run the migration).
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V4__dsl_audit_hash_chain.sql"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to set up in-memory dsl_audit table", e);
    }
    var context = new AnnotationConfigApplicationContext();
    context.registerBean("auditDataSource", DataSource.class, () -> dataSource);
    context.registerBean("transactionManager", PlatformTransactionManager.class,
            () -> new DataSourceTransactionManager(dataSource));
    context.registerBean(NamedParameterJdbcOperations.class,
            () -> new NamedParameterJdbcTemplate(dataSource));
    context.register(AuditRepositoryConfig.class);
    context.refresh();
    LAST_CONTEXT.set(context);
    var txTemplate = new TransactionTemplate(
            (PlatformTransactionManager) context.getBean("transactionManager"));
    var store = new DslAuditStore(context.getBean(DslAuditCrudRepository.class),
            new DslAuditMapperImpl(), txTemplate);
    return new Harness(new DslAuditService(store, new ObjectMapper()), store);
  }

  /**
   * Registers the Spring Data proxy for {@link DslAuditCrudRepository} over the in-memory H2
   * {@code dsl_audit} table, mirroring the {@code @EnableJdbcRepositories} scan of
   * {@code DslRunRepositoryConfiguration}.
   */
  @Configuration
  @EnableJdbcRepositories(basePackageClasses = DslAuditCrudRepository.class, considerNestedRepositories = false, includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DslAuditCrudRepository.class))
  static class AuditRepositoryConfig extends AbstractJdbcConfiguration {
  }

  public static ObjectProvider<DslAuditService> providerOf(DslAuditService service) {
    return new ObjectProvider<>() {
      @Override
      public DslAuditService getObject() {
        return service;
      }

      @Override
      public DslAuditService getIfAvailable() {
        return service;
      }

      @Override
      public DslAuditService getIfUnique() {
        return service;
      }
    };
  }

  public static ObjectProvider<DslAuditService> emptyProvider() {
    return new ObjectProvider<>() {
      @Override
      public DslAuditService getObject() {
        return null;
      }

      @Override
      public DslAuditService getIfAvailable() {
        return null;
      }

      @Override
      public DslAuditService getIfUnique() {
        return null;
      }
    };
  }

  /**
   * Test-only stub for an absent {@link JavaMailSender} bean — mirrors the absent-bean state
   * exercised by {@code EmailRuleSinkTest} and the wiring smoke checks in
   * {@code NotificationConfigurationTest}.
   */
  public static ObjectProvider<JavaMailSender> emptyMailSenderProvider() {
    return new ObjectProvider<>() {
      @Override
      public JavaMailSender getObject() {
        return null;
      }

      @Override
      public JavaMailSender getIfAvailable() {
        return null;
      }

      @Override
      public JavaMailSender getIfUnique() {
        return null;
      }
    };
  }
}
