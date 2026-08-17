package cbs.nova.dsl.fake;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class FakeDslDefinitionProvider implements DslDefinitionProvider {

  @Override
  public @NonNull List<DslObject> definitions() {
    return Dsl.process("SpiLoadedProcess")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("spi-loaded"))
            .buildList();
  }
}
