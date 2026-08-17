package cbs.nova.dslexamples;

public class VersionProbeModels {

  public record VersionProbeIn(String payload) {
  }

  public record VersionProbeOut(String result) {
  }
}
