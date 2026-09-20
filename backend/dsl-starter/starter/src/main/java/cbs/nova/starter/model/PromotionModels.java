package cbs.nova.starter.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request/response models for the T569 environment-promotion workflow. Promotion moves a bundle of
 * published definition metadata from one configured environment workbench directory to another over
 * the shared filesystem (phase 1; cross-host HTTP promotion is a follow-up).
 */
public final class PromotionModels {

  private PromotionModels() {
  }

  public record PromotionEnvironment(String name) {

  }

  public record PromotionDefinition(String name, String type, String status) {

  }

  /**
   * Body of {@code POST /api/dsl/promote}. {@code definitions} selects a subset of the source
   * environment's definitions; empty or absent promotes everything. {@code includeDrafts} also
   * considers draft markers on the source (published markers win per name).
   */
  public record PromotionRequest(
          String source,
          String target,
          @JsonInclude(JsonInclude.Include.NON_NULL) List<String> definitions,
          @JsonInclude(JsonInclude.Include.NON_NULL) Boolean includeDrafts) {

  }
}
