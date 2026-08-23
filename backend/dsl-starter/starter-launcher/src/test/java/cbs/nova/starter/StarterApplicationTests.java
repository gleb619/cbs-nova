package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StarterApplicationTests {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    assertThat(GlobalManager.globalManager()).isNotNull();
  }

  @Test
  void arithmeticHelperIsRegistered() {
    var helper = GlobalManager.globalManager().findHelper("arithmetic");
    assertThat(helper).isPresent();
  }

}
