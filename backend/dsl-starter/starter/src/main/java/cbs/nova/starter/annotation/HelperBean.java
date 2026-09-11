package cbs.nova.starter.annotation;

import org.springframework.context.annotation.Bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring {@link Bean @Bean} factory method whose return value should also be registered as
 * a DSL {@link cbs.nova.dsl.Executable} helper.
 *
 * <p>
 * The annotation is meta-annotated with {@code @Bean}, so the method behaves exactly like a regular
 * {@code @Bean} factory (autowired parameters, lifecycle callbacks, bean name defaults to the
 * method name, etc.). After the context starts, the resulting singleton is registered in the DSL
 * runtime under the name given by {@link #value()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Bean
public @interface HelperBean {

  /**
   * DSL helper name. Must be unique across all helpers and functions in the runtime registry.
   */
  String value();

}
