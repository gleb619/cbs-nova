package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDocsHandler;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.service.DslDocsService;
import cbs.nova.starter.service.DocNameResolver;
import cbs.nova.starter.service.DocTemplateService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
  private DslDocsService docsService;
  private DocNameResolver docNameResolver;

  @BeforeEach
  void setUp() {
    docsService = mock(DslDocsService.class);
    docNameResolver = mock(DocNameResolver.class);
    DslProperties properties = DslProperties.builder().sourceDir("/tmp/dsl").build();
    handler = new DslDocsHandler(properties, docsService, docNameResolver, new DocTemplateService(),
            new ObjectMapper());
  }

  @Test
  void readByNameReturnsDocContentWhenDocResolved() throws Exception {
    when(docNameResolver.existingDocPath("BatchProcessing"))
            .thenReturn(Optional.of("batch-processing.md"));
    when(docsService.readDoc("batch-processing.md"))
            .thenReturn(new FileContentResponse("batch-processing.md", "# Batch",
                    FileContentResponse.crc32("# Batch")));

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(renderBody(response)).contains("\"content\":\"# Batch\"");
  }

  @Test
  void readByNameReturns404WhenNoDocExists() throws IOException {
    when(docNameResolver.existingDocPath("Missing")).thenReturn(Optional.empty());

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/docs/by-name/Missing", "Missing"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readByNameReturns409WhenSourceDirNotConfigured() throws IOException {
    DslProperties properties = DslProperties.builder().build();
    handler = new DslDocsHandler(properties, docsService, docNameResolver, new DocTemplateService(),
            new ObjectMapper());

    ServerResponse response = handler.readByName(
            request("GET", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing"));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void writeByNameStagesWriteToResolvedDocPath() throws IOException {
    when(docNameResolver.existingDocPath("BatchProcessing"))
            .thenReturn(Optional.of("batch-processing.md"));

    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing",
                    "new doc"));

    assertThat(response.statusCode().value()).isEqualTo(202);
    verify(docsService).stageDocWrite("batch-processing.md",
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# BatchProcessing\n\nnew doc\n");
  }

  @Test
  void writeByNameFallsBackToDefaultKebabPathForNewDocs() throws IOException {
    when(docNameResolver.existingDocPath(anyString())).thenReturn(Optional.empty());
    when(docNameResolver.defaultDocPath("BatchProcessing")).thenReturn("batch-processing.md");

    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing", "BatchProcessing",
                    "new doc"));

    assertThat(response.statusCode().value()).isEqualTo(202);
    verify(docsService).stageDocWrite("batch-processing.md",
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# BatchProcessing\n\nnew doc\n");
  }

  @Test
  void writeByNameFillsNameAndBodyHeadingForANewDoc() throws IOException {
    when(docNameResolver.existingDocPath("BatchProcessing")).thenReturn(Optional.empty());
    when(docNameResolver.defaultDocPath("BatchProcessing")).thenReturn("batch-processing.md");

    handler.writeByName(requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing",
            "BatchProcessing", ""));

    verify(docsService).stageDocWrite("batch-processing.md",
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# BatchProcessing\n");
  }

  @Test
  void writeByNameKeepsAUserSuppliedHeading() throws IOException {
    when(docNameResolver.existingDocPath("BatchProcessing")).thenReturn(Optional.empty());
    when(docNameResolver.defaultDocPath("BatchProcessing")).thenReturn("batch-processing.md");

    handler.writeByName(requestWithBody("POST", "/api/dsl/docs/by-name/BatchProcessing",
            "BatchProcessing", "# My own title\n\nprose\n"));

    verify(docsService).stageDocWrite("batch-processing.md",
            "---\nname: BatchProcessing\ndescription: \"\"\n---\n\n# My own title\n\nprose\n");
  }

  @Test
  void listReturnsDocEntries() throws IOException {
    when(docsService.listDocs(null)).thenReturn(List.of());

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
}
