package cbs.nova.api.client;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "cbs.nova.api.client")
public class CbsNovaClientAutoConfiguration {}
