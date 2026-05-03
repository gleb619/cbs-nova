package cbs.nova.controller;

import cbs.nova.model.SettingCreateDto;
import cbs.nova.model.SettingDto;
import cbs.nova.model.SettingUpdateDto;
import cbs.nova.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Settings management API")
public class SettingController
    implements AbstractCrudController<SettingDto, SettingCreateDto, SettingUpdateDto, Long> {

  private final SettingService service;

  @Override
  public ResponseEntity<List<SettingDto>> findAll() {
    log.info("Fetching all settings");
    return ResponseEntity.ok(service.findAll());
  }

  @Override
  public ResponseEntity<SettingDto> findById(@PathVariable Long id) {
    log.info("Fetching setting with id: {}", id);
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
    log.info("Fetching setting with code: {}", code);
    return ResponseEntity.ok(service.findByCode(code));
  }

  @PostMapping
  @Override
  public ResponseEntity<SettingDto> create(@RequestBody SettingCreateDto dto) {
    log.info("Creating new setting: {}", dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
  }

  @Override
  public ResponseEntity<SettingDto> update(
      @PathVariable Long id, @RequestBody SettingUpdateDto dto) {
    log.info("Updating setting with id: {} with data: {}", id, dto);
    return ResponseEntity.ok(service.update(id, dto));
  }

  @Override
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    log.info("Deleting setting with id: {}", id);
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
