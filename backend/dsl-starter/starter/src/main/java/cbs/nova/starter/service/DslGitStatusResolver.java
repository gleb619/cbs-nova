package cbs.nova.starter.service;

import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.starter.builder.DslBuilderClient;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DslGitStatusResolver {

  private final ObjectProvider<DslBuilderClient> builderClientProvider;

  public DslGitStatusResolver(ObjectProvider<DslBuilderClient> builderClientProvider) {
    this.builderClientProvider = builderClientProvider;
  }

  public Optional<RepoStatus> status(Path candidateDir) {
    return builderClient().vcsStatus();
  }

  public static Optional<ChangeType> matchChange(Map<String, ChangeType> changes, String path) {
    if (path == null || path.isBlank() || changes == null || changes.isEmpty()) {
      return Optional.empty();
    }
    ChangeType direct = changes.get(path);
    if (direct != null) {
      return Optional.of(direct);
    }
    String suffix = "/" + path;
    for (Map.Entry<String, ChangeType> e : changes.entrySet()) {
      String k = e.getKey();
      if (k != null && k.endsWith(suffix)) {
        return Optional.of(e.getValue());
      }
    }
    String pathSuffix = "/" + path;
    for (Map.Entry<String, ChangeType> e : changes.entrySet()) {
      String k = e.getKey();
      if (k != null && pathSuffix.endsWith("/" + k)) {
        return Optional.of(e.getValue());
      }
    }
    return Optional.empty();
  }

  private DslBuilderClient builderClient() {
    DslBuilderClient client = builderClientProvider == null
            ? null
            : builderClientProvider.getIfAvailable();
    if (client == null) {
      throw new IllegalStateException("DslBuilderClient not available");
    }
    return client;
  }
}
