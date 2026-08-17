package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ServiceLoader;

@Slf4j
public final class ServiceLoaderDslDefinitionLoader implements DslDefinitionLoader {

  @Override
  public int load(@NonNull GlobalManager gm) {
    return load(Thread.currentThread().getContextClassLoader(), gm);
  }

  @Override
  public int load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    throw new UnsupportedOperationException(
            "ServiceLoaderDslDefinitionLoader does not support loading from source directories");
  }

  @Override
  public int load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm) {
    var providers = ServiceLoader.load(DslDefinitionProvider.class, classLoader);
    var iterator = providers.iterator();
    if (!iterator.hasNext()) {
      log.warn(
              "[ServiceLoaderDslDefinitionLoader] No DslDefinitionProvider on classpath — registry stays empty");
      return 0;
    }
    AtomicInteger counter = new AtomicInteger();
    iterator.forEachRemaining(provider -> {
      for (var obj : provider.definitions()) {
        counter.getAndIncrement();
        register(obj, gm);
      }
    });

    return counter.get();
  }

  private void register(@NonNull DslObject obj, @NonNull GlobalManager gm) {
    switch (obj.type()) {
      case PROCESS -> gm.registerProcess((ProcessDslObject) obj);
      case TRANSACTION -> gm.registerTransaction((TransactionDslObject) obj);
      case FUNCTION -> gm.registerFunction((FunctionDslObject) obj);
    }
  }
}
