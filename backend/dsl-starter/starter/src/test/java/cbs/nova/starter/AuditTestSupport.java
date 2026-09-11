package cbs.nova.starter;

import cbs.nova.starter.persistence.DslAuditRepository;
import cbs.nova.starter.service.DslAuditService;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a real {@link DslAuditService} + {@link DslAuditRepository} pair over a private in-memory
 * H2 database (V2 migration applied), so handler unit tests can assert that audit rows actually
 * land for each audited mutation.
 */
public final class AuditTestSupport {

  private AuditTestSupport() {
  }

  /**
   * Live audit stack: {@code service} is what handlers record through, {@code repository} is what
   * tests assert against.
   */
  public record Harness(DslAuditService service, DslAuditRepository repository) {
  }

  public static Harness h2() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:audit-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    try {
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V2__dsl_audit.sql"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to set up in-memory dsl_audit table", e);
    }
    var repository = new DslAuditRepository(new NamedParameterJdbcTemplate(dataSource));
    return new Harness(new DslAuditService(repository, new ObjectMapper()), repository);
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
}
