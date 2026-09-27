package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslFileHandler;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.service.DslFileService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

class DslFileHandlerTest {

  private static final List<HttpMessageConverter<?>> CONVERTERS = List.of(
          new StringHttpMessageConverter(),
          new JacksonJsonHttpMessageConverter());

  private DslFileHandler handler;
  private DslFileService fileService;

  @BeforeEach
  void setUp() {
    fileService = mock(DslFileService.class);
    DslProperties properties = DslProperties.builder().sourceDir("/tmp/dsl").build();
    handler = new DslFileHandler(properties, fileService, new ObjectMapper());
  }

  @Test
  void readByNameReturnsFileContent() throws Exception {
    when(fileService.readFileByName("LoanDisbursement"))
            .thenReturn(new FileContentResponse("dsl/LoanDisbursementDsl.java", "step A {}",
                    FileContentResponse.crc32("step A {}")));

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/files/by-name/LoanDisbursement", "LoanDisbursement"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(renderBody(response)).contains("\"content\":\"step A {}\"");
  }

  @Test
  void readByNameReturns404WhenBuilderReportsNotFound() throws IOException {
    when(fileService.readFileByName("Missing"))
            .thenThrow(builderNotFound("no DSL file for name: Missing"));

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/files/by-name/Missing", "Missing"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readByNameReturns409WhenSourceDirNotConfigured() throws IOException {
    DslProperties properties = DslProperties.builder().build();
    handler = new DslFileHandler(properties, fileService, new ObjectMapper());

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/files/by-name/LoanDisbursement", "LoanDisbursement"));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void writeByNameStagesWriteAndReturnsBuilderResolvedPath() throws Exception {
    when(fileService.stageWriteByName("ReserveInventory", "new content"))
            .thenReturn(new FileContentResponse("dsl/ReserveInventoryDsl.java", "new content",
                    FileContentResponse.crc32("new content")));

    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/files/by-name/ReserveInventory",
                    "ReserveInventory", "new content"));

    assertThat(response.statusCode().value()).isEqualTo(202);
    assertThat(renderBody(response)).contains("\"path\":\"dsl/ReserveInventoryDsl.java\"");
  }

  @Test
  void writeByNameReturns404WhenBuilderReportsNotFound() throws IOException {
    when(fileService.stageWriteByName("Missing", "x"))
            .thenThrow(builderNotFound("no DSL file for name: Missing"));

    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/files/by-name/Missing", "Missing", "x"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  private static BuilderApiException builderNotFound(String message) {
    return new BuilderApiException(HttpStatusCode.valueOf(404), "NOT_FOUND", message);
  }

  private static ServerRequest request(String method, String path, String name) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest requestWithBody(String method, String path, String name,
          String body) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContent(body.getBytes());
    req.setContentType("text/plain");
    return ServerRequest.create(req, CONVERTERS);
  }

  private static String renderBody(ServerResponse response) throws Exception {
    var servletResponse = new MockHttpServletResponse();
    response.writeTo(
            new MockHttpServletRequest("GET", "/api/dsl/files/by-name/test"),
            servletResponse,
            () -> List.of(new JacksonJsonHttpMessageConverter()));
    return servletResponse.getContentAsString();
  }
}
