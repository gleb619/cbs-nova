package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslFileHandler;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.service.DslFileService;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslFileHandlerTest {

  private DslFileHandler handler;
  private DslFileService fileService;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    fileService = mock(DslFileService.class);
    DslProperties properties = new DslProperties();
    properties.setSourceDir("/tmp/dsl");
    handler = new DslFileHandler(properties, fileService, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void readByNameReturnsFileContentWhenFilenameResolved() throws Exception {
    registerProvider("LoanDisbursement", "LoanDisbursementDsl.java");
    when(fileService.readFile("LoanDisbursementDsl.java"))
            .thenReturn(new FileContentResponse("LoanDisbursementDsl.java", "step A {}", false));

    ServerResponse response = handler
            .readByName(request("GET", "/api/dsl/files/by-name/LoanDisbursement"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(renderBody(response)).contains("\"content\":\"step A {}\"");
  }

  @Test
  void readByNameReturns404WhenFilenameUnknown() throws IOException {
    ServerResponse response = handler.readByName(request("GET", "/api/dsl/files/by-name/Missing"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readByNameReturns409WhenSourceDirNotConfigured() throws IOException {
    DslProperties properties = new DslProperties();
    handler = new DslFileHandler(properties, fileService, new ObjectMapper());

    ServerResponse response = handler
            .readByName(request("GET", "/api/dsl/files/by-name/LoanDisbursement"));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void writeByNameStagesWriteWhenFilenameResolved() throws IOException {
    registerProvider("ReserveInventory", "ReserveInventoryDsl.java");

    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/files/by-name/ReserveInventory", "new content"));

    assertThat(response.statusCode().value()).isEqualTo(202);
    verify(fileService).stageWrite("ReserveInventoryDsl.java", "new content");
  }

  @Test
  void writeByNameReturns404WhenFilenameUnknown() throws IOException {
    ServerResponse response = handler.writeByName(
            requestWithBody("POST", "/api/dsl/files/by-name/Missing", "x"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  private static void registerProvider(String name, String filename) {
    GeneratedClassProvider provider = new GeneratedClassProvider() {
      @Override
      public GeneratedClassDescriptor descriptor() {
        return new GeneratedClassDescriptor(
                name,
                DslObject.DslType.PROCESS,
                "v1",
                "default",
                Runnable.class,
                Runnable.class,
                String.class,
                String.class,
                "{}");
      }

      @Override
      public String filename() {
        return filename;
      }
    };
    GlobalManager.globalManager().registerGeneratedClass(provider);
  }

  private static ServerRequest request(String method, String path) {
    return ServerRequest.create(new MockHttpServletRequest(method, path), List.of());
  }

  private static ServerRequest requestWithBody(String method, String path, String body) {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest(method, path);
    servletRequest.setContent(body.getBytes());
    return ServerRequest.create(servletRequest, List.of());
  }

  private static String renderBody(ServerResponse response) throws Exception {
    var servletResponse = new MockHttpServletResponse();
    var servletRequest = new MockHttpServletRequest("GET",
            "/api/dsl/files/by-name/LoanDisbursement");
    response.writeTo(
            servletRequest,
            servletResponse,
            () -> List.of(new JacksonJsonHttpMessageConverter()));
    return servletResponse.getContentAsString();
  }
}
