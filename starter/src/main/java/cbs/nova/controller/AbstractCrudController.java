package cbs.nova.controller;

import cbs.nova.model.AbstractCrudDto;
import cbs.nova.model.AbstractCrudDto.AbstractCreateDto;
import cbs.nova.model.AbstractCrudDto.AbstractUpdateDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@SecurityRequirement(name = "bearerAuth")
public interface AbstractCrudController<
    T extends AbstractCrudDto<ID>, C extends AbstractCreateDto, U extends AbstractUpdateDto, ID> {

  @GetMapping
  @Operation(summary = "Get all entities", description = "Retrieve a list of all entities")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
      @ApiResponse(responseCode = "429", description = "Too many requests"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "Service unavailable")
  })
  ResponseEntity<List<T>> findAll();

  @GetMapping("/{id}")
  @Operation(summary = "Get entity by ID", description = "Retrieve a specific entity by its ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successfully retrieved entity"),
      @ApiResponse(responseCode = "400", description = "Invalid ID format"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
      @ApiResponse(responseCode = "404", description = "Entity not found"),
      @ApiResponse(responseCode = "429", description = "Too many requests"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "Service unavailable")
  })
  ResponseEntity<T> findById(
      @Parameter(description = "ID of the entity to retrieve") @PathVariable ID id);

  @PostMapping
  @Operation(summary = "Create entity", description = "Create a new entity")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Successfully created entity"),
      @ApiResponse(responseCode = "400", description = "Invalid input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
      @ApiResponse(responseCode = "409", description = "Entity already exists"),
      @ApiResponse(responseCode = "422", description = "Unprocessable entity - validation failed"),
      @ApiResponse(responseCode = "429", description = "Too many requests"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "Service unavailable")
  })
  ResponseEntity<T> create(@Parameter(description = "Entity data to create") @RequestBody C dto);

  @PutMapping("/{id}")
  @Operation(summary = "Update entity", description = "Update an existing entity")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successfully updated entity"),
      @ApiResponse(responseCode = "400", description = "Invalid input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
      @ApiResponse(responseCode = "404", description = "Entity not found"),
      @ApiResponse(responseCode = "409", description = "Conflict - entity state mismatch"),
      @ApiResponse(responseCode = "422", description = "Unprocessable entity - validation failed"),
      @ApiResponse(responseCode = "429", description = "Too many requests"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "Service unavailable")
  })
  ResponseEntity<T> update(
      @Parameter(description = "ID of the entity to update") @PathVariable ID id,
      @Parameter(description = "Updated entity data") @RequestBody U dto);

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete entity", description = "Delete a specific entity by its ID")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Successfully deleted entity"),
      @ApiResponse(responseCode = "400", description = "Invalid ID format"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
      @ApiResponse(responseCode = "404", description = "Entity not found"),
      @ApiResponse(responseCode = "409", description = "Conflict - entity cannot be deleted"),
      @ApiResponse(responseCode = "429", description = "Too many requests"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "Service unavailable")
  })
  ResponseEntity<Void> delete(
      @Parameter(description = "ID of the entity to delete") @PathVariable ID id);
}
