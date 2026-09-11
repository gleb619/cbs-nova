package cbs.nova.starter.config;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.annotation.HelperBean;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.MethodMetadata;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class HelperBeanRegistryInitializer
        implements
          SmartInitializingSingleton,
          ApplicationRunner,
          ApplicationContextAware,
          GlobalManagerReplacedListener {

  private ApplicationContext applicationContext;
  private GlobalManager registeredWith;

  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterSingletonsInstantiated() {
    // The DSL runtime may reset GlobalManager later via an ordered ApplicationRunner. Be lenient
    // here so a duplicate from a previous Spring context (static singleton not yet reset) does not
    // fail context startup. Strict validation happens in the ApplicationRunner / reload hook after
    // the runtime has settled on the final GlobalManager for this context.
    registerHelperBeans(true);
  }

  @Override
  public void run(ApplicationArguments args) {
    GlobalManager current = GlobalManager.globalManager();
    if (registeredWith != current) {
      registerHelperBeans(false);
    }
  }

  @Override
  public void onGlobalManagerReplaced(@NonNull GlobalManager globalManager) {
    // A reload has swapped in a brand-new GlobalManager. Clear our tracking so we re-register the
    // Spring-managed helpers into the fresh registry.
    registeredWith = null;
    registerHelperBeans(false);
  }

  private void registerHelperBeans(boolean lenient) {
    boolean registeredAny = false;
    boolean deferredAny = false;
    ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext)
            .getBeanFactory();
    for (String beanName : beanFactory.getBeanDefinitionNames()) {
      BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
      if (!(definition instanceof AnnotatedBeanDefinition annotated)) {
        continue;
      }
      MethodMetadata factoryMethod = annotated.getFactoryMethodMetadata();
      if (factoryMethod == null || !factoryMethod.isAnnotated(HelperBean.class.getName())) {
        continue;
      }
      var attributes = factoryMethod.getAnnotationAttributes(HelperBean.class.getName());
      if (attributes == null) {
        continue;
      }
      String helperName = (String) attributes.get("value");
      if (helperName == null || helperName.isBlank()) {
        throw new IllegalStateException(
                "@HelperBean value must not be blank for bean: " + beanName);
      }
      Object bean = applicationContext.getBean(beanName);
      if (!(bean instanceof Executable<?, ?> executable)) {
        throw new IllegalStateException(
                "@HelperBean bean must implement Executable: " + bean.getClass().getName());
      }
      GlobalManager globalManager = GlobalManager.globalManager();
      if (globalManager.hasHelper(helperName)) {
        var existing = globalManager.findHelper(helperName).orElse(null);
        if (existing == bean) {
          if (lenient) {
            log.debug("@HelperBean '{}' already registered as '{}' with the same instance; "
                    + "deferring re-registration until after GlobalManager reset",
                    beanName, helperName);
            deferredAny = true;
          } else {
            log.debug("@HelperBean '{}' already registered as '{}' with the same instance; "
                    + "skipping", beanName, helperName);
          }
          continue;
        }
        if (lenient) {
          log.debug("@HelperBean '{}' ('{}') already registered with a different instance; "
                  + "deferring re-registration until after GlobalManager reset",
                  beanName, helperName);
          deferredAny = true;
          continue;
        }
        throw new IllegalStateException(
                "Helper name already registered with a different instance: " + helperName);
      }
      log.debug("Registering @HelperBean '{}' as DSL helper '{}'", beanName, helperName);
      globalManager.registerHelper(helperName, executable);
      registeredAny = true;
    }
    if (registeredAny && !deferredAny) {
      registeredWith = GlobalManager.globalManager();
    }
  }
}
