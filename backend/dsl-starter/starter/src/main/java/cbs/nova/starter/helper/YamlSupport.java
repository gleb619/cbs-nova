package cbs.nova.starter.helper;

import static cbs.nova.starter.core.StarterConstants.YAML_MAX_CODE_POINTS;

import cbs.nova.starter.core.StarterConstants;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class YamlSupport {

  /**
   * Returns a {@link Yaml} configured for safe loading. All custom tags are rejected via the
   * {@code TagInspector} set on the {@link LoaderOptions}; booleans follow strict YAML 1.2
   * semantics via {@link Yaml12Resolver}.
   */
  static Yaml safeLoader() {
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setAllowDuplicateKeys(false);
    loaderOptions.setMaxAliasesForCollections(50);
    loaderOptions.setCodePointLimit(YAML_MAX_CODE_POINTS);
    // Reject every global tag. Built-in scalars (!!str/!!int/!!float/!!bool/!!null) and the
    // implicit-tag short forms are handled by SnakeYAML's SafeConstructor without consulting the
    // inspector, so this still permits the natural YAML 1.2 type set.
    loaderOptions.setTagInspector(tag -> false);
    DumperOptions dumperOptions = new DumperOptions();
    Representer representer = new Representer(dumperOptions);
    Resolver resolver = new Yaml12Resolver();
    return new Yaml(
            new SafeConstructor(loaderOptions), representer, dumperOptions, loaderOptions,
            resolver);
  }

  /**
   * Returns a {@link Yaml} configured for emitting canonical YAML 1.2 block-style output.
   */
  static Yaml dumper() {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumperOptions.setPrettyFlow(true);
    dumperOptions.setIndent(2);
    return new Yaml(dumperOptions);
  }

  /**
   * Resolver that follows YAML 1.2 boolean semantics: only {@code true}/{@code false} (any case)
   * match the implicit {@code !!bool} tag. The YAML 1.1 aliases {@code yes}/{@code no}/{@code on}/
   * {@code off} stay as plain strings so DSL authors do not accidentally rely on the legacy trap.
   * All other implicit resolvers are inherited from {@link Resolver} unchanged.
   */
  private static final class Yaml12Resolver extends Resolver {

    private static final Pattern YAML_1_2_BOOL = Pattern
            .compile("^(?:true|True|TRUE|false|False|FALSE)$");

    Yaml12Resolver() {
      // Re-register a tighter BOOL resolver for the two relevant starting characters.
      addImplicitResolver(Tag.BOOL, YAML_1_2_BOOL, "tf");
      // Strip the inherited YAML 1.1 aliases (y, n, o) so they fall through to the generic VALUE
      // resolver and remain plain strings.
      this.yamlImplicitResolvers.get('y').clear();
      this.yamlImplicitResolvers.get('n').clear();
      this.yamlImplicitResolvers.get('o').clear();
    }
  }
}
