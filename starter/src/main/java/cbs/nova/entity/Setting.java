package cbs.nova.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Setting entity")
public class Setting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Schema(description = "Unique identifier of the setting", example = "1")
  private Long id;

  @Column(nullable = false, unique = true)
  @Schema(
      description = "Unique code for the setting",
      example = "max_login_attempts",
      required = true)
  private String code;

  @Column(nullable = false)
  @Schema(description = "Value of the setting", example = "5", required = true)
  private String value;

  @Schema(
      description = "Description of what the setting controls",
      example = "Maximum number of login attempts before account lockout")
  private String description;
}
