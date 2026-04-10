package cbs.nova.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Setting data transfer object")
public class SettingDto {

  @Schema(description = "Unique identifier of the setting", example = "1")
  private Long id;

  @Schema(
      description = "Unique code for the setting",
      example = "max_login_attempts",
      required = true)
  private String code;

  @Schema(description = "Value of the setting", example = "5", required = true)
  private String value;

  @Schema(
      description = "Description of what the setting controls",
      example = "Maximum number of login attempts before account lockout")
  private String description;
}
