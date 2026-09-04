package cbs.nova.starter.helper;

import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Shared YAML factory for {@link ParseYamlHelper} and {@link FormatYamlHelper}.
 *
 * <p>
 * Locked security options (see plan T344, "Locked Decisions" 2026-09-04):
 *
 * <ul>
 * <li>{@code LoaderOptions.setAllowDuplicateKeys(false)} — duplicate keys are rejected, matching
 * strict YAML 1.2 parsers.
 * <li>{@code LoaderOptions.setMaxAliasesForCollections(50)} — bounds the well-known billion-laughs
 * style alias expansion.
 * <li>{@code LoaderOptions.setCodePointLimit(3 * 1024 * 1024)} — caps a single document at 3 MiB of
 * Unicode code points (Temporal default activity payload limit).
 * <li>{@code LoaderOptions.setTagInspector(tag -> false)} — every global tag is rejected. Only
 * built-in types (strings, integers, floats, booleans, null, sequences, maps) are permitted. This
 * is the snakeyaml CVE-2017-18640 mitigation: a payload like
 * {@code !!javax.scripting.ScriptEngineManager {}} is refused before any class is instantiated.
 * <li>{@link Yaml12Resolver} — only {@code true}/{@code false} (any case) parse as boolean. The
 * YAML 1.1 aliases {@code yes}/{@code no}/{@code on}/{@code off} stay as plain strings.
 * <li>{@code DumperOptions.setDefaultFlowStyle(BLOCK)} with {@code setPrettyFlow(true)} and
 * {@code setIndent(2)} — deterministic, human-readable block style.
 * </ul>
 */
final class YamlSupport {

  private static final int MAX_CODE_POINTS = 3 * 1024 * 1024;

  private YamlSupport() {
  }

  /**
   * Returns a {@link Yaml} configured for safe loading. All custom tags are rejected via the
   * {@code TagInspector} set on the {@link LoaderOptions}; booleans follow strict YAML 1.2
   * semantics via {@link Yaml12Resolver}.
   */
  static Yaml safeLoader() {
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setAllowDuplicateKeys(false);
    loaderOptions.setMaxAliasesForCollections(50);
    loaderOptions.setCodePointLimit(MAX_CODE_POINTS);
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
