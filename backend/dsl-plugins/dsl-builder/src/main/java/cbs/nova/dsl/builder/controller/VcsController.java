package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
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

  @GetMapping("/status")
  public RepoStatus status() {
    return gitStatusService.status(properties.workspaceDir())
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no git repository found under workspace"));
  }
}
