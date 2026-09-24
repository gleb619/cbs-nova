package cbs.nova.starter.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * One-release backwards-compatibility bridge for the misspelled {@code csb.dsl.*} property prefix.
 *
 * <p>
 * Any property starting with {@code csb.dsl.} is copied to the canonical {@code cbs.dsl.} name when
 * the canonical key is absent. This lets existing deployments keep using the old prefix while the
 * starter's own {@code @ConditionalOnProperty} and {@code @ConfigurationProperties} annotations
 * only reference {@code cbs.dsl.*}.
 */
@Slf4j
public class CbsDslLegacyPropertyPrefixPostProcessor implements EnvironmentPostProcessor {

  private static final String LEGACY_PREFIX = "csb.dsl.";
  private static final String CANONICAL_PREFIX = "cbs.dsl.";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment,
          SpringApplication application) {
    Map<String, Object> migrated = new LinkedHashMap<>();
    Set<String> legacyKeys = collectLegacyKeys(environment);
    for (String legacyKey : legacyKeys) {
      String canonicalKey = CANONICAL_PREFIX + legacyKey.substring(LEGACY_PREFIX.length());
      if (!environment.containsProperty(canonicalKey)) {
        Object value = environment.getProperty(legacyKey);
        if (value != null) {
          migrated.put(canonicalKey, value);
        }
      }
    }
    if (!migrated.isEmpty()) {
      MapPropertySource source = new MapPropertySource("legacy-csb-dsl-properties", migrated);
      environment.getPropertySources().addFirst(source);
      log.warn(
              "Deprecated csb.dsl.* property keys were migrated to cbs.dsl.* for this release: {}",
              migrated.keySet());
    }
  }

  private static Set<String> collectLegacyKeys(ConfigurableEnvironment environment) {
    Set<String> keys = new LinkedHashSet<>();
    MutablePropertySources sources = environment.getPropertySources();
    for (PropertySource<?> source : sources) {
      if (source instanceof EnumerablePropertySource<?> enumerable) {
        for (String name : enumerable.getPropertyNames()) {
          if (name.startsWith(LEGACY_PREFIX)) {
            keys.add(name);
          }
        }
      }
    }
    return keys;
  }
}
