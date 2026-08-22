package cbs.nova.starter.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(SpringHelperBeanDefinitionRegistrar.class)
public class SpringHelperAutoConfiguration {
}
