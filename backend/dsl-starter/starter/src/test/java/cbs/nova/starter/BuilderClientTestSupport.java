package cbs.nova.starter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.ObjectProvider;

final class BuilderClientTestSupport {

  private BuilderClientTestSupport() {
  }

  @SuppressWarnings("unchecked")
  static ObjectProvider<DslBuilderClient> providerOf(DslBuilderClient client) {
    ObjectProvider<DslBuilderClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(client);
    return provider;
  }

  static void stubSuccessfulCompile(DslBuilderClient client) {
    when(client.compile(any(CompileRequest.class)))
            .thenReturn(new CompileResult("s-1", true, List.of(), List.of(), 1));
    when(client.downloadZip("s-1")).thenReturn(emptyZip());
  }

  static byte[] emptyZip() {
    var out = new ByteArrayOutputStream();
    try (var ignored = new ZipOutputStream(out)) {
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
