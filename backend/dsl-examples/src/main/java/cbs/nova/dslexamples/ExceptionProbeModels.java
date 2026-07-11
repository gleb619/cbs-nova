package cbs.nova.dslexamples;

public class ExceptionProbeModels {

  public record ExceptionProbeIn(boolean shouldFail, String reason) {
  }

  public record ExceptionProbeOut(String result) {
  }
}
