package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDocsHandler;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.FileEntityKind;
import cbs.nova.starter.service.DslFileService;
import cbs.nova.starter.service.DocTemplateService;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import java.io.IOException;
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

class DslDocsHandlerTest {

  private static final List<HttpMessageConverter<?>> CONVERTERS = List.of(
          new StringHttpMessageConverter(),
          new JacksonJsonHttpMessageConverter());

  private DslDocsHandler handler;
  private DslFileService fileService;

  @BeforeEach
  void setUp() {
    fileService = mock(DslFileService.class);
    DslProperties properties = DslProperties.builder().sourceDir("/tmp/dsl").build();
    handler = new DslDocsHandler(properties, fileService, new DocTemplateService(),
            new ObjectMapper());
  }

  @Test
  void readByNameReturnsDocContent() throws Exception {
    when(fileService.readFileByName("BatchProcessing", FileEntityKind.DOC))
            .thenReturn(new FileContentResponse("docs/batch-processing.md", "# Batch",
                    FileContentResponse.crc32("# Batch")));

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(renderBody(response)).contains("\"content\":\"# Batch\"");
  }

  @Test
  void readByNameReturns404WhenBuilderReportsNotFound() throws IOException {
    when(fileService.readFileByName("Missing", FileEntityKind.DOC))
            .thenThrow(new BuilderApiException(HttpStatusCode.valueOf(404), "NOT_FOUND",
                    "no doc for name: Missing"));

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/docs/by-name/Missing", "Missing"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readByNameReturns409WhenSourceDirNotConfigured() throws IOException {
    DslProperties properties = DslProperties.builder().build();
    handler = new DslDocsHandler(properties, fileService, new DocTemplateService(),
            new ObjectMapper());

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing"));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void writeByNameStagesStructuredContentAndReturnsBuilderResolvedPath() throws Exception {
    String structured = "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n"
            + "# BatchProcessing\n\nnew doc\n";
    when(fileService.stageWriteByName(eq("BatchProcessing"), eq(structured),
            eq(FileEntityKind.DOC)))
            .thenReturn(new FileContentResponse("docs/batch-processing.md", structured,
                    FileContentResponse.crc32(structured)));

    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing",
                    "new doc"));

    assertThat(response.statusCode().value()).isEqualTo(202);
    verify(fileService).stageWriteByName("BatchProcessing", structured, FileEntityKind.DOC);
    assertThat(renderBody(response)).contains("\"path\":\"docs/batch-processing.md\"");
  }

  @Test
  void writeByNameFillsNameAndBodyHeadingForANewDoc() throws IOException {
    String structured = "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n"
            + "# BatchProcessing\n";
    when(fileService.stageWriteByName(eq("BatchProcessing"), eq(structured),
            eq(FileEntityKind.DOC)))
            .thenReturn(new FileContentResponse("docs/batch-processing.md", structured,
                    FileContentResponse.crc32(structured)));

    handler.writeByName(requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing",
            "BatchProcessing", ""));

    verify(fileService).stageWriteByName("BatchProcessing", structured, FileEntityKind.DOC);
  }

  @Test
  void writeByNameKeepsAUserSuppliedHeading() throws IOException {
    String structured = "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n"
            + "# My own title\n\nprose\n";
    when(fileService.stageWriteByName(eq("BatchProcessing"), eq(structured),
            eq(FileEntityKind.DOC)))
            .thenReturn(new FileContentResponse("docs/batch-processing.md", structured,
                    FileContentResponse.crc32(structured)));

    handler.writeByName(requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing",
            "BatchProcessing", "# My own title\n\nprose\n"));

    verify(fileService).stageWriteByName("BatchProcessing", structured, FileEntityKind.DOC);
  }

  @Test
  void listReturnsDocEntries() throws IOException {
    when(fileService.listFiles(null, FileEntityKind.DOC)).thenReturn(List.of());

    ServerResponse response = handler.list(request("GET", "/api/dsl/docs", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
  }

  private static ServerRequest request(String method, String path, String name) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, path);
    if (name != null) {
      req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    }
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
            new MockHttpServletRequest("GET", "/api/dsl/docs/by-name/test"),
            servletResponse,
            () -> List.of(new JacksonJsonHttpMessageConverter()));
    return servletResponse.getContentAsString();
  }

  @Test
  void bulkWriteRespectsGlobalRequestBodyLimit() {
    // The router's RequestBodyLimitFilter rejects oversized bodies before the controller runs.
    // DslExceptionHandler then maps the DslPayloadTooLargeException to a 413 with the
    // PAYLOAD_TOO_LARGE ErrorResponse. We assert the filter short-circuit here; the response
    // shape is pinned by DslExceptionHandlerTest.
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
            new cbs.nova.starter.config.properties.CbsNovaWebProperties(8L));
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/dsl/docs/bulk");
    req.setContentType("application/json");
    req.setContent(("{\"entries\":[" + "{\"data\":\"" + "x".repeat(4096) + "\"}".repeat(50) + "]}")
            .getBytes());
    ServerRequest oversized = ServerRequest.create(req, CONVERTERS);

    assertThatThrownBy(() -> filter.filter(oversized, handler::bulkWrite))
            .isInstanceOfSatisfying(DslPayloadTooLargeException.class,
                    ex -> assertThat(ex.getEntityName())
                            .isEqualTo(RequestBodyLimitFilter.ENTITY_NAME));
  }
}
