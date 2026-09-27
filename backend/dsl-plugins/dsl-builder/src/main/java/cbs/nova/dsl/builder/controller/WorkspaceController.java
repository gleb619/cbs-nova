package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.service.WorkspaceService;
import cbs.nova.dsl.builder.service.WorkspaceService.WorkspaceInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the builder's workspace mode and the actual directory backing DSL file I/O
 * (docs/backend/drafts.md §1). The starter consumes this so metadata reports the real source
 * location instead of guessing from its own {@code cbs.dsl.source-dir}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/workspace")
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  @GetMapping
  public WorkspaceInfo workspace() {
    return workspaceService.workspaceInfo();
  }
}
