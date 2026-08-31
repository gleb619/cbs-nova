package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.DslDefinitionBundleRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class DslDefinitionBundleResourceTest {

  private MockMvc mockMvc;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    sourceDir = Files.createTempDirectory("dsl-bundle-test-");
    DslProperties props = new DslProperties();
    props.setSourceDir(sourceDir.toString());

    DslDefinitionHistoryService historyService = new DslDefinitionHistoryService(props, mapper);
    DslDefinitionBundleService bundleService = new DslDefinitionBundleService(mapper,
            Optional.empty());
    DslDraftHandler handler = new DslDraftHandler(
            props,
            new DslReloadHandler(props, null),
            historyService,
            mapper,
            bundleService);
    DslDefinitionBundleRouterConfiguration router = new DslDefinitionBundleRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslDefinitionBundleRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter(),
                    new InputStreamHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (sourceDir != null && Files.exists(sourceDir)) {
      deleteRecursively(sourceDir);
    }
  }

  @Test
  void exportReturnsPublishedBundle() throws Exception {
    publish("A", "v1");
    publish("B", "v2");

    String json = mockMvc.perform(get("/api/dsl/definitions/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"formatVersion\":1");
    assertThat(json).contains("\"name\":\"A\"");
    assertThat(json).contains("\"name\":\"B\"");
    assertThat(json).contains("\"source\":\"published\"");
    assertThat(json).contains("\"engineVersion\"");
    assertThat(json).contains("\"exportedAt\"");
  }

  @Test
  void exportWithDraftsIncludesDraftsAndPublishesWin() throws Exception {
    publish("A", "v1");
    draft("C", "v3");
    draft("A", "draft-v1");

    String json = mockMvc.perform(get("/api/dsl/definitions/export?include=drafts"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"name\":\"A\"");
    assertThat(json).contains("\"name\":\"C\"");
    assertThat(json).contains("\"source\":\"published\"");
    assertThat(json).contains("\"source\":\"draft\"");
    assertThat(json).doesNotContain("\"version\":\"draft-v1\"");
  }

  @Test
  void importRoundTripRecreatesPublishedMarkers() throws Exception {
    publish("A", "v1");
    publish("B", "v2");
    String bundle = mockMvc.perform(get("/api/dsl/definitions/export"))
            .andReturn().getResponse().getContentAsString();

    deleteRecursively(sourceDir.resolve(".workbench/published"));

    String result = mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content(bundle))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(result).contains("\"dryRun\":false");
    assertThat(result).contains("\"published\":2");
    assertThat(result).contains("\"outcome\":\"published\"");
    assertThat(sourceDir.resolve(".workbench/published/A.json")).exists();
    assertThat(sourceDir.resolve(".workbench/published/B.json")).exists();
  }

  @Test
  void dryRunImportDoesNotWriteFiles() throws Exception {
    String bundle = "{\"formatVersion\":1,\"definitions\":["
            + "{\"definition\":{\"name\":\"A\",\"type\":\"process\",\"status\":\"Published\",\"version\":\"v1\",\"taskQueue\":\"q\"},\"source\":\"published\"}]}";

    String result = mockMvc.perform(post("/api/dsl/definitions/import?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content(bundle))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(result).contains("\"dryRun\":true");
    assertThat(result).contains("\"outcome\":\"skipped\"");
    assertThat(sourceDir.resolve(".workbench/published/A.json")).doesNotExist();
  }

  @Test
  void importBadFormatVersionReturns400() throws Exception {
    String body = "{\"formatVersion\":99,\"definitions\":["
            + "{\"definition\":{\"name\":\"A\",\"type\":\"process\",\"status\":\"Published\",\"version\":\"v1\"},\"source\":\"published\"}]}";

    String result = mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

    assertThat(result).contains("99");
  }

  @Test
  void importMalformedJsonReturns400() throws Exception {
    mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content("not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("INVALID_REQUEST"));
  }

  @Test
  void importEmptyDefinitionsReturns400() throws Exception {
    String body = "{\"formatVersion\":1,\"definitions\":[]}";

    mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("no definitions"));
  }

  @Test
  void importTooManyDefinitionsReturns400() throws Exception {
    StringBuilder sb = new StringBuilder("{\"formatVersion\":1,\"definitions\":[");
    for (int i = 0; i < 201; i++) {
      if (i > 0)
        sb.append(",");
      sb.append("{\"definition\":{\"name\":\"D").append(i)
              .append("\",\"type\":\"process\",\"status\":\"Published\",\"version\":\"v1\"},\"source\":\"published\"}");
    }
    sb.append("]}");

    mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content(sb.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("too large"));
  }

  @Test
  void importSnapshotsPreviousPublishedPayload() throws Exception {
    publish("A", "v1");
    Thread.sleep(2);
    String bundle = "{\"formatVersion\":1,\"definitions\":["
            + "{\"definition\":{\"name\":\"A\",\"type\":\"process\",\"status\":\"Published\",\"version\":\"v2\",\"taskQueue\":\"q\"},\"source\":\"published\"}]}";

    mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content(bundle))
            .andExpect(status().isOk());

    Path historyDir = sourceDir.resolve(".workbench/history/A");
    assertThat(historyDir).isDirectory();
    List<Path> files;
    try (Stream<Path> s = Files.list(historyDir)) {
      files = s.filter(Files::isRegularFile).toList();
    }
    assertThat(files).hasSize(1);
    DraftRequest snapshot = mapper.readValue(files.get(0).toFile(), DraftRequest.class);
    assertThat(snapshot.version()).isEqualTo("v1");
  }

  @Test
  void exportReturns409WhenSourceDirBlank() throws Exception {
    buildHandlerWithBlankSourceDir();

    mockMvc.perform(get("/api/dsl/definitions/export"))
            .andExpect(status().isConflict())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("NOT_CONFIGURED"));
  }

  @Test
  void importReturns409WhenSourceDirBlank() throws Exception {
    buildHandlerWithBlankSourceDir();

    mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isConflict())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("NOT_CONFIGURED"));
  }

  private void buildHandlerWithBlankSourceDir() {
    DslProperties blank = new DslProperties();
    blank.setSourceDir("");
    DslDraftHandler handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));
    DslDefinitionBundleRouterConfiguration router = new DslDefinitionBundleRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslDefinitionBundleRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter(),
                    new InputStreamHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  private void publish(String name, String version) throws Exception {
    Path dir = sourceDir.resolve(".workbench/published");
    Files.createDirectories(dir);
    DraftRequest req = new DraftRequest(name, "process", "Published", version, "q");
    Files.writeString(dir.resolve(name + ".json"), mapper.writeValueAsString(req),
            StandardCharsets.UTF_8);
  }

  private void draft(String name, String version) throws Exception {
    Path dir = sourceDir.resolve(".workbench/drafts");
    Files.createDirectories(dir);
    DraftRequest req = new DraftRequest(name, "process", "Draft", version, "q");
    Files.writeString(dir.resolve(name + ".json"), mapper.writeValueAsString(req),
            StandardCharsets.UTF_8);
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

  private void deleteRecursively(Path path) throws Exception {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> stream = Files.walk(path)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (Exception e) {
          // ignore
        }
      });
    }
  }

}
