package cbs.nova.starter.config;

import cbs.nova.starter.annotation.SpringHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.List;

@Slf4j
public class SpringHelperBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

  private static final AnnotationBeanNameGenerator NAME_GENERATOR = AnnotationBeanNameGenerator.INSTANCE;

  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
          BeanDefinitionRegistry registry) {
    if (!(registry instanceof ConfigurableBeanFactory beanFactory)) {
      return;
    }
    List<String> packages;
    try {
      packages = AutoConfigurationPackages.get(beanFactory);
    } catch (IllegalStateException ex) {
      log.debug("No auto-configuration base packages registered; skipping @SpringHelper scan", ex);
      return;
    }
    if (packages.isEmpty()) {
      return;
    }
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(SpringHelper.class));
    for (String pkg : packages) {
      var candidates = scanner.findCandidateComponents(pkg);
      for (BeanDefinition candidate : candidates) {
        String beanName = NAME_GENERATOR.generateBeanName(candidate, registry);
        if (registry.containsBeanDefinition(beanName)) {
          continue;
        }
        var definition = new GenericBeanDefinition(candidate);
        definition.setScope(BeanDefinition.SCOPE_SINGLETON);
        registry.registerBeanDefinition(beanName, definition);
      }
    }
  }
}