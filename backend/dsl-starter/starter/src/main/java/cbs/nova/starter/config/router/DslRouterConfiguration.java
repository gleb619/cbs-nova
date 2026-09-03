package cbs.nova.starter.config.router;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Aggregator that wires all functional DSL/executions routers into the starter auto-configuration.
 *
 * <p>
 * Each imported router is an independent {@code @Configuration}; this class keeps
 * {@link DslRootConfiguration} from growing an unwieldy import list.
 */
@Configuration(proxyBeanMethods = false)
@Import({
    DslExecutionsRouterConfiguration.class,
    DslIntrospectionRouterConfiguration.class,
    WebhookRouterConfiguration.class,
    DslRuntimeRouterConfiguration.class,
    DslReloadRouterConfiguration.class,
    DslDraftRouterConfiguration.class,
    DslFileRouterConfiguration.class
})
public class DslRouterConfiguration {
}
