package cbs.nova.starter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SpringHelperBeanDefinitionRegistrar.class)
public class SpringHelperConfiguration {
}
