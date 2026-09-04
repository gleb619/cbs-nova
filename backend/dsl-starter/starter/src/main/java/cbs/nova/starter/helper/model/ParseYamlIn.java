package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code parseYaml} helper.
 *
 * <p>
 * {@code payload} is a YAML 1.2 document. The parser uses snakeyaml's {@code SafeConstructor} with
 * a {@code TagInspector} that rejects all custom tags, so untrusted input cannot instantiate Java
 * classes (see CVE-2017-18640).
 */
public record ParseYamlIn(String payload) {
}
