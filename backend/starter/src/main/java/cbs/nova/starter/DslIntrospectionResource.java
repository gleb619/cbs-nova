package cbs.nova.starter;

import cbs.nova.dsl.GlobalManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dsl")
public class DslIntrospectionResource {

  @GetMapping("/processes")
  public ResponseEntity<NamesResponse> processes() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.getInstance().processNames()));
  }

  @GetMapping("/processes/{name}")
  public ResponseEntity<?> processDetail(@PathVariable String name) {
    return GlobalManager.getInstance()
            .findProcess(name)
            .<ResponseEntity<?>>map(
                    p -> ResponseEntity.ok(
                            new ProcessDetail(
                                    p.name(),
                                    p.version(),
                                    p.taskQueue(),
                                    typeName(p.inputType()),
                                    typeName(p.outputType()),
                                    p.compensationLogic() != null)))
            .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/transactions")
  public ResponseEntity<NamesResponse> transactions() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.getInstance().transactionNames()));
  }

  @GetMapping("/transactions/{name}")
  public ResponseEntity<?> transactionDetail(@PathVariable String name) {
    return GlobalManager.getInstance()
            .findTransaction(name)
            .<ResponseEntity<?>>map(
                    t -> ResponseEntity.ok(
                            new TransactionDetail(
                                    t.name(),
                                    t.version(),
                                    t.taskQueue(),
                                    typeName(t.inputType()),
                                    typeName(t.outputType()),
                                    t.compensationLogic() != null,
                                    t.startToCloseTimeout().toMillis())))
            .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/helpers")
  public ResponseEntity<NamesResponse> helpers() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.getInstance().helperNames()));
  }

  private static String typeName(Class<?> type) {
    return type == null ? null : type.getSimpleName();
  }

  public record NamesResponse(List<String> names) {
  }

  public record ProcessDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation) {
  }

  public record TransactionDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
          long startToCloseTimeoutMs) {
  }
}
