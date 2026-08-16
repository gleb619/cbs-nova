package cbs.nova.server;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.server.helpers.GreeterHelper;
import cbs.nova.server.helpers.GreeterIn;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTests {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    assertThat(GlobalManager.globalManager()).isNotNull();
  }

  @Test
  void greeterHelperBeanExists() {
    var bean = applicationContext.getBean(GreeterHelper.class);
    assertThat(bean).isNotNull();
  }

  @Test
  void greeterHelperIsRegistered() {
    var helper = GlobalManager.globalManager().findHelper("greeter");
    assertThat(helper).isPresent();
  }

  @Test
  void greeterHelperCanBeInvoked() {
    var helper = applicationContext.getBean(GreeterHelper.class);
    var baseCtx = GlobalManager.globalManager()
        .createContext(Map.of(), Map.of(), cbs.nova.dsl.ExecutionMode.PREVIEW, "test-run");
    var ctx = baseCtx.withBody(new GreeterIn("Nova"));
    var result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().message()).isEqualTo("Hello, Nova!");
  }
}
