package cbs.nova.model;

import cbs.nova.model.AbstractCrudDto.AbstractUpdateDto;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SettingUpdateDto implements AbstractUpdateDto {

  @NonNull
  @Positive
  private Long id;

  @JsonUnwrapped
  private SettingCreateDto dto;
}
