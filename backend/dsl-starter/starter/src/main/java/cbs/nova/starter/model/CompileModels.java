package cbs.nova.starter.model;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompileModels {

  public record CompileRequest(
          String buildVersion,
          String targetPackage,
          String basePackage,
          Boolean useFileNameSubPackage,
          String repoUrl,
          String baseBranch,
          Map<String, String> sources) {
  }

  public record CompileResult(
          String id,
          boolean success,
          List<String> generatedFiles,
          List<String> diagnostics,
          long durationMillis) {
  }

  public record CompileErrorResponse(String error, List<String> diagnostics) {
  }
}
