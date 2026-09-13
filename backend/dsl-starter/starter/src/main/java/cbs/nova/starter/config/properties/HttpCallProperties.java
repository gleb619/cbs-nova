package cbs.nova.starter.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cbs.dsl.helper.http-call")
public record HttpCallProperties(
        List<String> allowedSchemes,
        @DefaultValue("true") boolean blockPrivateAddresses,
        List<String> allowedHosts) {

  public HttpCallProperties {
    allowedSchemes = allowedSchemes == null
            ? List.of("https", "http")
            : List.copyOf(allowedSchemes);
    allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
  }

  /**
   * Legacy permissive guard config: no private-address blocking, no host allowlist. Used by the
   * two-argument {@code HttpCallHelper} convenience constructor so manually wired call sites
   * (tests, examples) keep their pre-guard behaviour. The Spring-managed bean binds this record
   * from {@code cbs.dsl.helper.http-call} and therefore gets the secure defaults.
   */
  public static HttpCallProperties permissive() {
    return new HttpCallProperties(List.of("https", "http"), false, List.of());
  }
}
