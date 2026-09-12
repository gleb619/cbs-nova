package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.exception.CompileException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GitService {

  public Path cloneRepository(String repoUrl, Path targetDir, String branch) {
    try {
      var clone = Git.cloneRepository().setURI(repoUrl).setDirectory(targetDir.toFile());
      if (branch != null && !branch.isBlank()) {
        clone.setBranch(branch);
      }
      clone.call();
      return targetDir;
    } catch (GitAPIException e) {
      throw compileError("clone", e);
    }
  }

  private CompileException compileError(String operation, Exception e) {
    log.warn("git {} failed: {}", operation, e.getMessage());
    return new CompileException("git %s failed: %s".formatted(operation, e.getMessage()),
            List.of(e.getMessage()));
  }
}
