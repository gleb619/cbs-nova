package cbs.nova.starter;

import cbs.nova.dsl.GlobalManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

  @GetMapping("/transactions")
  public ResponseEntity<NamesResponse> transactions() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.getInstance().transactionNames()));
  }

  @GetMapping("/helpers")
  public ResponseEntity<NamesResponse> helpers() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.getInstance().helperNames()));
  }

  public record NamesResponse(List<String> names) {
  }
}
