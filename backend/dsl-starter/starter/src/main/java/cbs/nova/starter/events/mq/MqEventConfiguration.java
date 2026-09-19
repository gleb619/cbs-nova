package cbs.nova.starter.events.mq;

import cbs.nova.starter.config.properties.MqEventProperties;
import cbs.nova.starter.events.sink.DomainEventSink;
import cbs.nova.starter.events.sink.MqDomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Wiring for the optional RabbitMQ domain-event publisher. Imported from
 * {@link cbs.nova.starter.config.DslRootAutoConfiguration}; this class is deliberately NOT
 * annotated {@code @Configuration} so it is evaluated only after the auto-configuration phase.
 */
@ConditionalOnClass(name = "org.springframework.amqp.core.AmqpTemplate")
@ConditionalOnProperty(name = "cbs.nova.events.mq.enabled", havingValue = "true")
public class MqEventConfiguration {

  @Bean
  @ConditionalOnBean(DslEventRepository.class)
  public DomainEventSink mqDomainEventSink(AmqpTemplate amqpTemplate,
          ObjectMapper objectMapper,
          DslEventRepository repository,
          MqEventProperties properties) {
    return new MqDomainEventSink(amqpTemplate, objectMapper, repository, properties);
  }

  @Bean(name = "cbsNovaMqRetryExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnBean(DslEventRepository.class)
  public ScheduledExecutorService cbsNovaMqRetryExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-mq-retry");
      t.setDaemon(true);
      return t;
    });
  }

  @Bean
  @ConditionalOnBean(DslEventRepository.class)
  public MqDomainEventRetryTask mqDomainEventRetryTask(DslEventRepository repository,
          MqDomainEventSink sink,
          MqEventProperties properties,
          ScheduledExecutorService cbsNovaMqRetryExecutor) {
    return new MqDomainEventRetryTask(repository, sink, properties,
            cbsNovaMqRetryExecutor, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean(MqDomainEventRetryTask.class)
  public ApplicationRunner mqDomainEventRetryTaskStarter(MqDomainEventRetryTask task) {
    return args -> task.start();
  }
}
