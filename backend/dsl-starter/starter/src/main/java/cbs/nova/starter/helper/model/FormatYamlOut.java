package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code formatYaml} helper.
 *
 * <p>
 * {@code yaml} is the canonical YAML 1.2 block-style representation emitted by snakeyaml.
 */
public record FormatYamlOut(String yaml) {
}
