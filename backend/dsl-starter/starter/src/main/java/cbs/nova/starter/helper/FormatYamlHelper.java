package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.FormatYamlIn;
import cbs.nova.starter.helper.model.FormatYamlOut;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Emits a nested {@link Map}, {@link java.util.List}, or scalar tree as a canonical YAML 1.2
 * block-style string.
 *
 * <p>
 * Output uses {@code DumperOptions.FlowStyle.BLOCK} with two-space indent — the format most
 * authoring tools (Kubernetes manifests, GitHub Actions workflows, Helm values, OpenAPI specs) emit
 * and accept.
 *
 * <p>
 * Empty or {@code null} input is rejected as a typed helper error. Top-level scalars are permitted;
 * they serialize as plain YAML scalars.
 */
@Helper(name = "formatYaml")
public class FormatYamlHelper implements Executable<FormatYamlIn, FormatYamlOut> {

  @Override
  public @NonNull Result<FormatYamlOut> execute(@NonNull Context<FormatYamlIn> ctx) {
    try {
      FormatYamlIn input = ctx.body();
      if (input.data() == null) {
        return Result.failure(new IllegalArgumentException("formatYaml.data is required"));
      }
      if (input.data() instanceof Map<?, ?> map && map.isEmpty()) {
        return Result.failure(new IllegalArgumentException("formatYaml.data is required"));
      }
      if (input.data() instanceof Iterable<?> iterable) {
        boolean empty = true;
        for (Object ignored : iterable) {
          empty = false;
          break;
        }
        if (empty) {
          return Result.failure(new IllegalArgumentException("formatYaml.data is required"));
        }
      }
      Yaml yaml = YamlSupport.dumper();
      String dumped = yaml.dump(input.data());
      if (dumped == null || dumped.isEmpty()) {
        return Result.failure(new IllegalArgumentException("formatYaml.data is required"));
      }
      return Result.success(new FormatYamlOut(dumped));
    } catch (YAMLException e) {
      return Result.failure(new IllegalArgumentException("formatYaml: malformed YAML", e));
    } catch (IllegalArgumentException e) {
      return Result.failure(e);
    } catch (RuntimeException e) {
      return Result.failure(new IllegalArgumentException("formatYaml: malformed YAML", e));
    }
  }
}
