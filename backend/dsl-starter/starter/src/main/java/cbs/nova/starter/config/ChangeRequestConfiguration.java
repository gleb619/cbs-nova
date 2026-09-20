package cbs.nova.starter.config;

import cbs.nova.starter.service.ChangeRequestService;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.persistence.ChangeRequestRepository;
import cbs.nova.starter.service.DslAuditService;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

// NOTE: deliberately NOT annotated @Configuration — like NotificationConfiguration this class
// is wired only through the @Import in DslRootAutoConfiguration. A @Configuration class in the
// scanned cbs.nova.starter tree would be picked up by host/test component scans BEFORE the
// DataSource bean definition exists, and its @ConditionalOnBean(DataSource.class) methods would
// silently back off.

/**
 * T568 change-request approval gate wiring. Everything is conditional on a {@link DataSource}: the
 * change-request store is JDBC-backed. The service additionally resolves {@link DslDraftHandler}
 * lazily so the approval gate composes with (but does not require) the drafts surface.
 */
public class ChangeRequestConfiguration {

  @Bean
  @ConditionalOnBean(DataSource.class)
  ChangeRequestRepository changeRequestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    return new ChangeRequestRepository(jdbcTemplate);
  }

  @Bean
  @ConditionalOnBean(ChangeRequestRepository.class)
  ChangeRequestService changeRequestService(ChangeRequestRepository repository,
          DslProperties dslProperties, ObjectMapper objectMapper,
          ObjectProvider<DslDraftHandler> draftHandlerProvider,
          ObjectProvider<DslAuditService> auditService) {
    return new ChangeRequestService(repository, dslProperties, objectMapper, draftHandlerProvider,
            auditService);
  }
}
