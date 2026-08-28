package cbs.nova.starter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.StarterITApplication;
import cbs.nova.starter.annotation.SpringHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = StarterITApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:springhelper-testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "temporal.connection-target=127.0.0.1:1",
    "dsl.worker.enabled=false"
})
public class SpringHelperIntegrationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void springHelperIsABeanAndRegisteredAsDslHelper() {
    SpringGreetHelper bean = applicationContext.getBean(SpringGreetHelper.class);
    assertThat(bean).isNotNull();
    assertThat(GlobalManager.globalManager().hasHelper("springGreet")).isTrue();
  }

  @SpringHelper(name = "springGreet")
  public static final class SpringGreetHelper implements Executable<String, String> {

    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success("hello " + ctx.body());
    }
  }
}
