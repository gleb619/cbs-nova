package cbs.nova.starter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Aggregator that wires all functional DSL/executions routers into the starter auto-configuration.
 *
 * <p>
 * Each imported router is an independent {@code @AutoConfiguration}; this class keeps
 * {@link DslRootAutoConfiguration} from growing an unwieldy import list.
 */
@Configuration(proxyBeanMethods = false)
@Import({
    DslExecutionsRouterConfiguration.class,
    DslIntrospectionRouterConfiguration.class,
    DslRuntimeRouterConfiguration.class,
    DslReloadRouterConfiguration.class,
    DslDraftRouterConfiguration.class
})
public class DslRouterConfiguration {
}
