package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundle;
import cbs.nova.dsl.builder.model.VcsModels.ImportBundleResult;
import cbs.nova.dsl.builder.service.DraftService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/definitions")
public class DefinitionBundleController {

  private final DraftService draftService;

  @GetMapping("/export")
  public DefinitionBundle export(@RequestParam(required = false) String include) {
    return draftService.exportBundle("drafts".equals(include));
  }

  @PostMapping("/import")
  public ImportBundleResult importBundle(@RequestBody DefinitionBundle bundle,
          @RequestParam(required = false, defaultValue = "false") boolean dryRun)
          throws IOException {
    return draftService.importBundle(bundle, dryRun);
  }
}
