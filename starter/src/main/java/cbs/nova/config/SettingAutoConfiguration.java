package cbs.nova.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(basePackages = "cbs.nova")
@EnableJpaRepositories(basePackages = "cbs.nova")
public class SettingAutoConfiguration {

}
