package cbs.nova.model;

import cbs.dsl.api.HelperTypes.HelperOutput;
import lombok.Builder;

@Builder(toBuilder = true)
public record HelperExecutionResponse(String executionId, HelperOutput output) {}