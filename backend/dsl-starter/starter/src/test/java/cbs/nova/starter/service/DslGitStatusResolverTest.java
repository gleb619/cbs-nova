package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.dsl.vcs.RepoStatus;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class DslGitStatusResolverTest {

  @TempDir
  Path tempDir;

  @Test
  void delegatesToBuilderClientWhenAvailable() {
    var expected = Optional.of(RepoStatus.fromDirtyPaths(tempDir, Set.of("builder.txt")));
    var builder = mock(DslBuilderClient.class);
    when(builder.vcsStatus()).thenReturn(expected);

    var resolver = newResolver(builder);

    assertThat(resolver.status(tempDir)).isSameAs(expected);
  }

  private static DslGitStatusResolver newResolver(DslBuilderClient client) {
    return new DslGitStatusResolver(provider(client));
  }

  private static ObjectProvider<DslBuilderClient> provider(DslBuilderClient client) {
    return new ObjectProvider<>() {
      @Override
      public DslBuilderClient getIfAvailable() {
        return client;
      }

      @Override
      public DslBuilderClient getObject() {
        return client;
      }

      @Override
      public DslBuilderClient getIfUnique() {
        return client;
      }
    };
  }
}
