package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.model.PageResponse;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.dsl.builder.model.VcsModels.DraftRequest;
import cbs.nova.dsl.builder.model.VcsModels.DraftResponse;
import cbs.nova.dsl.builder.model.VcsModels.DraftSummary;
import cbs.nova.dsl.builder.model.VcsModels.HistoryDiffResponse;
import cbs.nova.dsl.builder.service.DraftService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/drafts")
public class DraftController {

  private final DraftService draftService;

  @PostMapping("/{name}/save")
  public DraftResponse save(@PathVariable String name, @RequestBody DraftRequest body)
          throws IOException {
    return draftService.save(name, body);
  }

  @PostMapping("/{name}/publish")
  public DraftResponse publish(@PathVariable String name, @RequestBody DraftRequest body)
          throws IOException {
    return draftService.publish(name, body);
  }

  @GetMapping("/{name}/history")
  public List<DefinitionHistoryEntry> history(@PathVariable String name) {
    return draftService.history(name);
  }

  @GetMapping("/{name}/history/{timestamp}")
  public DraftRequest historyEntry(@PathVariable String name, @PathVariable String timestamp) {
    return draftService.historyEntry(name, timestamp);
  }

  @GetMapping("/{name}/history/{timestamp}/diff")
  public HistoryDiffResponse historyDiff(@PathVariable String name,
          @PathVariable String timestamp) throws IOException {
    return draftService.historyDiff(name, timestamp);
  }

  @PostMapping("/{name}/history/{timestamp}/restore")
  public DraftResponse restore(@PathVariable String name, @PathVariable String timestamp)
          throws IOException {
    return draftService.restore(name, timestamp);
  }

  @DeleteMapping("/{name}")
  public DraftResponse delete(@PathVariable String name) throws IOException {
    return draftService.delete(name);
  }

  @GetMapping
  public PageResponse<DraftSummary> list(@RequestParam(required = false) Integer limit,
          @RequestParam(required = false) Integer offset) {
    return draftService.list(limit, offset);
  }

  @GetMapping("/{name}")
  public DraftRequest read(@PathVariable String name) throws IOException {
    return draftService.read(name);
  }
}
