package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.DslDraftRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controllers.DslDraftHandler;
import cbs.nova.starter.controllers.DslReloadHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.ObjectMapper;

class DslDraftResourceTest {

  private DslDraftHandler handler;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-test-");
    DslProperties props = new DslProperties(sourceDir.toString(), null, null, null, null);
    handler = new DslDraftHandler(props, new DslReloadHandler(props, null), mapper);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (sourceDir != null && Files.exists(sourceDir)) {
      deleteRecursively(sourceDir);
    }
  }

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new InputStreamHttpMessageConverter());

  private static ServerRequest postRequest(String path) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", "foo"));
    req.setContentType("application/json");
    req.setContent("{\"name\":\"foo\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"1\"}"
            .getBytes());
    return ServerRequest.create(req, CONVERTERS);
  }

  @Test
  void savePersistsDraftJson() throws Exception {
    ServerResponse response = handler.save(postRequest("/api/dsl/drafts/foo/save"));
    assertThat(response.statusCode().value()).isEqualTo(200);
    Path draft = sourceDir.resolve(".workbench/drafts/foo.json");
    assertThat(draft).exists();
    String body = Files.readString(draft);
    assertThat(body).contains("\"name\" : \"foo\"");
    assertThat(body).contains("\"status\" : \"Draft\"");
  }

  @Test
  void publishPersistsPublishedJson() throws Exception {
    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish"));
    assertThat(response.statusCode().value()).isEqualTo(200);
    Path published = sourceDir.resolve(".workbench/published/foo.json");
    assertThat(published).exists();
    String body = Files.readString(published);
    assertThat(body).contains("\"status\" : \"Published\"");
  }

  @Test
  void saveReturns400WhenBodyInvalid() throws Exception {
    var req = new MockHttpServletRequest("POST", "/api/dsl/drafts/foo/save");
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", "foo"));
    req.setContentType("application/json");
    req.setContent("not-json".getBytes());
    ServerResponse response = handler.save(
            ServerRequest.create(req, CONVERTERS));
    assertThat(response.statusCode().value()).isEqualTo(400);
  }

  @Test
  void saveReturns409WhenSourceDirBlank() throws Exception {
    handler = new DslDraftHandler(
            new DslProperties("", null, null, null, null),
            new DslReloadHandler(new DslProperties("", null, null, null, null), null),
            mapper);
    ServerResponse response = handler.save(postRequest("/api/dsl/drafts/foo/save"));
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void routerFunctionIsRegisteredByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslDraftTestConfig.class,
                    DslDraftRouterConfiguration.class, DslDraftHandler.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(RouterFunction.class));
  }

  @Test
  void routerFunctionSkippedWhenDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslDraftTestConfig.class,
                    DslDraftRouterConfiguration.class, DslDraftHandler.class)
            .withPropertyValues("dsl.drafts.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
  }

  private void deleteRecursively(Path path) throws IOException {
    try (Stream<Path> stream = Files.walk(path)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          // ignore
        }
      });
    }
  }

  private static final class InputStreamHttpMessageConverter
          implements
            HttpMessageConverter<InputStream> {

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return InputStream.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
      return false;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
      return List.of(MediaType.ALL);
    }

    @Override
    public InputStream read(Class<? extends InputStream> clazz, HttpInputMessage inputMessage)
            throws IOException {
      return inputMessage.getBody();
    }

    @Override
    public void write(InputStream inputStream, MediaType contentType,
            HttpOutputMessage outputMessage) {
      throw new UnsupportedOperationException();
    }
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslDraftTestConfig {

    @Bean
    DslReloadHandler dslReloadHandler(DslProperties props) {
      return new DslReloadHandler(props, null);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
