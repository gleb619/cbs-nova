package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ServiceLoader;

@Slf4j
public final class DefinitionLoader implements DslDefinitionLoader {

  @Override
  public LoadResult load(@NonNull GlobalManager gm) {
    return load(gm.defaultClassLoader(), gm);
  }

  @Override
  public LoadResult load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    throw new UnsupportedOperationException(
            "DefinitionLoader does not support loading from source directories");
  }

  @Override
  public LoadResult load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm) {
    var providers = ServiceLoader.load(DslDefinitionProvider.class, classLoader);
    var iterator = providers.iterator();
    var result = LoadResult.builder();
    if (!iterator.hasNext()) {
      log.warn(
              "[DefinitionLoader] No DslDefinitionProvider on classpath — registry stays empty");
      return result.build();
    }
    iterator.forEachRemaining(provider -> {
      for (var obj : provider.definitions()) {
        register(obj, gm, result);
      }
    });

    return result.build();
  }

  private void register(@NonNull DslObject obj, @NonNull GlobalManager gm,
          LoadResult.@NonNull Builder result) {
    switch (obj.type()) {
      case PROCESS -> {
        gm.registerProcess((ProcessDslObject) obj);
        result.add(DslObject.DslType.PROCESS, obj.name());
      }
      case TRANSACTION -> {
        gm.registerTransaction((TransactionDslObject) obj);
        result.add(DslObject.DslType.TRANSACTION, obj.name());
      }
      case FUNCTION -> {
        gm.registerFunction((FunctionDslObject) obj);
        result.add(DslObject.DslType.FUNCTION, obj.name());
      }
    }
  }
}
