package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.service.FileService;
import cbs.nova.dsl.builder.service.GitStatusService;
import cbs.nova.dsl.builder.service.GitStatusService.RepoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/vcs")
public class VcsController {

  private final GitStatusService gitStatusService;
  private final DslBuilderProperties properties;
  private final FileService fileService;

  @GetMapping("/status")
  public RepoStatus status() {
    // Invariant I5: a pending write already counts as a draft. Flush the buffer first, then
    // drop the per-root snapshot so the next scan reflects the freshly-flushed file. The
    // flush itself also invalidates the cache, but we invalidate again here in case another
    // writer mutated the working tree in the meantime (e.g. the scheduled flusher).
    if (fileService.pendingCount() > 0) {
      fileService.flushPending();
      gitStatusService.invalidate();
    }
    return gitStatusService.status(properties.workspaceDir())
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no git repository found under workspace"));
  }
}
