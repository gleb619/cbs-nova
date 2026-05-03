package cbs.nova.controller;

import cbs.nova.bpmn.BpmnExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflows", description = "Workflow BPMN export API")
public class BpmnController {

  private final BpmnExporter bpmnExporter;

  @GetMapping(value = "/{code}/bpmn", produces = MediaType.APPLICATION_XML_VALUE)
  @Operation(summary = "Export workflow as BPMN 2.0 XML")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "BPMN 2.0 XML"),
      @ApiResponse(responseCode = "404", description = "Workflow not found"),
      @ApiResponse(responseCode = "501", description = "SVG format not implemented")
  })
  public ResponseEntity<String> getBpmn(
      @PathVariable String code, @RequestParam(required = false) String format) {
    if ("svg".equalsIgnoreCase(format)) {
      return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_XML)
        .body(bpmnExporter.export(code));
  }
}
