package cbs.nova.model.openapi;

import cbs.nova.model.AbstractCrudDto;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Setting model")
public interface SettingOpenApi<ID> extends AbstractCrudDto<ID> {

  @Override
  @Schema(
      description = "Unique identifier of the setting",
      example = "1",
      requiredMode = RequiredMode.AUTO)
  ID getId();

  @Schema(
      description = "Unique code for the setting",
      example = "max_login_attempts",
      requiredMode = RequiredMode.REQUIRED)
  String getCode();

  @Schema(description = "Value of the setting", example = "5", requiredMode = RequiredMode.REQUIRED)
  String getValue();

  @Schema(
      description = "Description of what the setting controls",
      example = "Maximum number of login attempts before account lockout",
      requiredMode = RequiredMode.NOT_REQUIRED)
  String getDescription();
}
