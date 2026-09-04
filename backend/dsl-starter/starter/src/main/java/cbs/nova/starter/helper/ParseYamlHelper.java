package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.ParseYamlIn;
import cbs.nova.starter.helper.model.ParseYamlOut;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Parses a YAML 1.2 document into a nested {@code Map<String, Object>}.
 *
 * <p>
 * Maps become {@code Map<String, Object>}, sequences become {@code java.util.List<Object>}, and
 * scalars become {@code String}, {@code Integer}, {@code Long}, {@code Double}, {@code Boolean}, or
 * {@code null}. YAML 1.1 boolean aliases ({@code yes}/{@code no}/{@code on}/{@code off}/
 * {@code y}/{@code n}) stay as plain strings — only {@code true}/{@code false} (any case) promote
 * to {@code Boolean}. The strict 1.2 set is enforced by a custom {@code Resolver} layered on top of
 * snakeyaml's {@link SafeConstructor}.
 *
 * <p>
 * The loader is hardened: {@code LoaderOptions.setAllowDuplicateKeys(false)},
 * {@code setMaxAliasesForCollections(50)}, {@code setCodePointLimit(3 MiB)}, and a
 * {@code TagInspector} that rejects every global tag (snakeyaml CVE-2017-18640 mitigation). A
 * payload like {@code !!javax.scripting.ScriptEngineManager {}} never instantiates the engine.
 */
@Helper(name = "parseYaml")
public class ParseYamlHelper implements Executable<ParseYamlIn, ParseYamlOut> {

  @Override
  public @NonNull Result<ParseYamlOut> execute(@NonNull Context<ParseYamlIn> ctx) {
    try {
      ParseYamlIn input = ctx.body();
      if (input.payload() == null || input.payload().isBlank()) {
        return Result.failure(new IllegalArgumentException("parseYaml.payload is required"));
      }
      Yaml yaml = YamlSupport.safeLoader();
      Object parsed = yaml.load(input.payload());
      if (parsed == null) {
        return Result.failure(new IllegalArgumentException("parseYaml: empty document"));
      }
      if (!(parsed instanceof Map<?, ?> map)) {
        return Result.failure(
                new IllegalArgumentException("parseYaml: top-level must be a mapping"));
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) map;
      return Result.success(new ParseYamlOut(data));
    } catch (YAMLException e) {
      return Result.failure(new IllegalArgumentException("parseYaml: malformed YAML", e));
    } catch (IllegalArgumentException e) {
      return Result.failure(e);
    } catch (RuntimeException e) {
      return Result.failure(new IllegalArgumentException("parseYaml: malformed YAML", e));
    }
  }
}
