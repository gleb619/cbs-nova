package cbs.nova.starter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.StarterITApplication;
import cbs.nova.starter.annotation.HelperBean;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * End-to-end verification that a {@code @HelperBean} factory method produces a Spring singleton and
 * registers it as a DSL helper under the configured name.
 */
@SpringBootTest(classes = StarterITApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:helperbean-testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "temporal.connection-target=127.0.0.1:1",
    "cbs.dsl.worker.enabled=false"
})
@Import(HelperBeanIntegrationTest.ProbeHelperConfiguration.class)
class HelperBeanIntegrationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void helperBeanIsABeanAndRegisteredAsDslHelper() {
    ProbeHelper bean = applicationContext.getBean(ProbeHelper.class);
    assertThat(bean).isNotNull();
    assertThat(GlobalManager.globalManager().hasHelper("helperBeanProbe")).isTrue();

    Context<?> ctx = GlobalManager.globalManager()
            .createContext("world", Map.of(), ExecutionMode.PREVIEW, "helper-bean-it");
    Result<?> result = GlobalManager.globalManager().runHelper("helperBeanProbe", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("probe: world");
  }

  @Configuration
  static class ProbeHelperConfiguration {

    @HelperBean("helperBeanProbe")
    public ProbeHelper probeHelper() {
      return new ProbeHelper();
    }
  }

  public static final class ProbeHelper implements Executable<String, String> {

    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success("probe: " + ctx.body());
    }
  }
}
