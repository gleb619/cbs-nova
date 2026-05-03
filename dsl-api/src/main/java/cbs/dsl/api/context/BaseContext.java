package cbs.dsl.api.context;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class BaseContext {

  private String eventCode;
  private Long workflowExecutionId;
  private String performedBy;
  private String dslVersion;

  public void println(String message) {
    System.out.println("[DSL] " + message);
  }
}
