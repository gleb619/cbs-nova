package cbs.nova.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(
    basePackages = {"cbs.nova.service", "cbs.nova.controller", "cbs.nova.mapper", "cbs.nova.bpmn"})
@EntityScan(basePackages = "cbs.nova.entity")
@EnableJpaRepositories(basePackages = "cbs.nova.repository")
public class NovaAutoConfiguration {}
