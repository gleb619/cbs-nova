package cbs.nova.dslexamples;

import java.util.List;

public class NestedCompensationModels {

  public record NestedCompensationIn(String jobId) {
  }

  public record NestedCompensationOut(String jobId, String status,
          List<CompensationLogEntry> compensationLog) {
  }

  public record CompensationLogEntry(String stepName, String message) {
  }
}
