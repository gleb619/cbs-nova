package cbs.nova.controller;

import cbs.nova.model.SettingCreateDto;
import cbs.nova.model.SettingDto;
import cbs.nova.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Settings management API")
@SecurityRequirement(name = "bearerAuth")
public class SettingController
    implements AbstractCrudController<SettingDto, SettingCreateDto, Long> {

  private final SettingService service;

  @Override
  public ResponseEntity<List<SettingDto>> findAll() {
    return ResponseEntity.ok(service.findAll());
  }

  @Override
  public ResponseEntity<SettingDto> findById(@PathVariable Long id) {
    return ResponseEntity.ok(service.findById(id));
  }

  @GetMapping("/code/{code}")
  @Operation(
      summary = "Get setting by code",
      description = "Retrieve a specific setting by its unique code")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successfully retrieved setting"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
      @ApiResponse(responseCode = "404", description = "Setting not found"),
      @ApiResponse(responseCode = "429", description = "Too many requests"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "Service unavailable")
  })
  public ResponseEntity<SettingDto> findByCode(
      @Parameter(description = "Unique code of the setting to retrieve") @PathVariable
      String code) {
    return ResponseEntity.ok(service.findByCode(code));
  }

  @PostMapping
  @Override
  public ResponseEntity<SettingDto> create(@RequestBody SettingCreateDto dto) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
  }

  @Override
  public ResponseEntity<SettingDto> update(@PathVariable Long id, @RequestBody SettingCreateDto dto) {
    return ResponseEntity.ok(service.update(id, dto));
  }

  @Override
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
