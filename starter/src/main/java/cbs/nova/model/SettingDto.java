package cbs.nova.model;

import cbs.nova.model.openapi.SettingOpenApi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SettingDto implements SettingOpenApi<Long> {

  private Long id;

  @NotBlank
  @Size(max = 100)
  private String code;

  @NotBlank
  private String value;

  @Size(max = 255)
  private String description;
}
