package cbs.nova.starter.events.mq;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.NotificationTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * T566: with the default {@code cbs.nova.events.mq.enabled=false} the starter must start without a
 * broker and must not create the MQ sink or retry beans.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = NotificationTestApplication.class)
@TestPropertySource(properties = {
    "cbs.dsl.worker.enabled=false",
    "cbs.nova.events.mq.enabled=false"
})
class MqEventDisabledIntegrationTest {

  @Autowired
  private ApplicationContext context;

  @Test
  void contextLoadsAndMqBeansAbsent() {
    assertThat(context.containsBean("mqDomainEventSink")).isFalse();
    assertThat(context.containsBean("mqDomainEventRetryTask")).isFalse();
  }
}
