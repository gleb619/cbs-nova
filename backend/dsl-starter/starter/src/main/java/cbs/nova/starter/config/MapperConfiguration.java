package cbs.nova.starter.config;

import cbs.nova.starter.converter.DslAuditMapper;
import cbs.nova.starter.converter.DslIntrospectionMapper;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.converter.DslRuntimeMapper;
import cbs.nova.starter.converter.TransactionExecutionMapper;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes MapStruct-generated mapper implementations as Spring beans for consumers that do not
 * component-scan {@code cbs.nova.starter.converter} themselves. Each bean is skipped when the host
 * application's own scan already registers the mapper.
 */
@Configuration
public class MapperConfiguration {

  @Bean
  @ConditionalOnMissingBean(DslRunMapper.class)
  public DslRunMapper dslRunMapper() {
    return instantiate(DslRunMapper.class, "cbs.nova.starter.converter.DslRunMapperImpl");
  }

  @Bean
  @ConditionalOnMissingBean(DslAuditMapper.class)
  public DslAuditMapper dslAuditMapper() {
    return instantiate(DslAuditMapper.class, "cbs.nova.starter.converter.DslAuditMapperImpl");
  }

  @Bean
  @ConditionalOnMissingBean(TransactionExecutionMapper.class)
  public TransactionExecutionMapper transactionExecutionMapper() {
    return instantiate(TransactionExecutionMapper.class,
            "cbs.nova.starter.converter.TransactionExecutionMapperImpl");
  }

  @Bean
  @ConditionalOnMissingBean(DslRuntimeMapper.class)
  public DslRuntimeMapper dslRuntimeMapper() {
    return instantiate(DslRuntimeMapper.class, "cbs.nova.starter.converter.DslRuntimeMapperImpl");
  }

  @Bean
  @ConditionalOnMissingBean(DslIntrospectionMapper.class)
  public DslIntrospectionMapper dslIntrospectionMapper() {
    return instantiate(DslIntrospectionMapper.class,
            "cbs.nova.starter.converter.DslIntrospectionMapperImpl");
  }

  private static <T> T instantiate(Class<T> mapperType, String implClassName) {
    try {
      return mapperType.cast(
              Class.forName(implClassName).getDeclaredConstructor().newInstance());
    } catch (Exception e) {
      throw new BeanCreationException("Failed to instantiate MapStruct mapper " + implClassName, e);
    }
  }
}
