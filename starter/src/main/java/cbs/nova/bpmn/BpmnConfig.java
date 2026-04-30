package cbs.nova.bpmn;

import cbs.nova.registry.DslRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class that wires BPMN-related beans. This approach is used instead of
 * annotating {@link BpmnExporter} with {@code @Service} to avoid violating the ArchUnit rule that
 * {@code @Service} must only live in {@code cbs.nova.service..}.
 */
@Configuration
public class BpmnConfig {

  @Bean
  public StaticBpmnGenerator staticBpmnGenerator() {
    return new StaticBpmnGenerator();
  }

  @Bean
  public BpmnExporter bpmnExporter(DslRegistry registry, StaticBpmnGenerator generator) {
    return new BpmnExporter(registry, generator);
  }
}
