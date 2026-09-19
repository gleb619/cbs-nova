package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.config.router.DslPromoteRouterConfiguration;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.controller.DslPromoteHandler;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

class DslPromoteResourceTest {

  private MockMvc mockMvc;
  private Path devDir;
  private Path stagingDir;
  private DslAuditService auditService;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    devDir = Files.createTempDirectory("dsl-promote-dev-");
    stagingDir = Files.createTempDirectory("dsl-promote-staging-");
    DslProperties props = DslProperties.builder()
            .sourceDir(devDir.toString())
            .promotion(new DslProperties.Promotion(Map.of(
                    "dev", new DslProperties.Promotion.Environment(devDir.toString()),
                    "staging", new DslProperties.Promotion.Environment(stagingDir.toString()))))
            .build();
    auditService = mock(DslAuditService.class);
    @SuppressWarnings("unchecked")
    org.springframework.beans.factory.ObjectProvider<DslAuditService> auditProvider = mock(
            org.springframework.beans.factory.ObjectProvider.class);
    when(auditProvider.getIfAvailable()).thenReturn(auditService);
    DslDefinitionBundleService bundleService = new DslDefinitionBundleService(mapper,
            Optional.empty(), DslProperties.bundleServiceDefaults());
    DslPromoteHandler handler = new DslPromoteHandler(
            props,
            bundleService,
            new DslDefinitionHistoryService(props, mapper),
            mapper,
            auditProvider);
    mockMvc = mockMvcFor(handler);
  }

  private MockMvc mockMvcFor(DslPromoteHandler handler) {
    DslPromoteRouterConfiguration router = new DslPromoteRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    return MockMvcBuilders.routerFunctions(router.dslPromoteRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter(),
                    new InputStreamHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    deleteRecursively(devDir);
    deleteRecursively(stagingDir);
  }

  @Test
  void environmentsListsConfiguredNames() throws Exception {
    String json = mockMvc.perform(get("/api/dsl/promote/environments"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"name\":\"dev\"");
    assertThat(json).contains("\"name\":\"staging\"");
  }

  @Test
  void definitionsListsSourceEnvironmentDefinitions() throws Exception {
    publish(devDir, "A", "v1");
    publish(devDir, "B", "v2");

    String json = mockMvc.perform(get("/api/dsl/promote/definitions").param("env", "dev"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"name\":\"A\"");
    assertThat(json).contains("\"name\":\"B\"");
    assertThat(json).contains("\"status\":\"Published\"");
  }

  @Test
  void definitionsUnknownEnvironmentReturns404() throws Exception {
    mockMvc.perform(get("/api/dsl/promote/definitions").param("env", "prod"))
            .andExpect(status().isNotFound())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("ENV_NOT_FOUND"));
  }

  @Test
  void dryRunShowsDiffWithoutWritingTarget() throws Exception {
    publish(devDir, "A", "v1");
    publish(devDir, "B", "v2");
    publish(stagingDir, "B", "v1");

    String json = mockMvc.perform(post("/api/dsl/promote?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"dryRun\":true");
    assertThat(json).contains("\"outcome\":\"created\"");
    assertThat(json).contains("\"outcome\":\"updated\"");
    assertThat(stagingDir.resolve(".workbench/published/A.json")).doesNotExist();
    verifyNoInteractions(auditService);
  }

  @Test
  void dryRunWithUnchangedDefinition() throws Exception {
    publish(devDir, "A", "v1");
    publish(stagingDir, "A", "v1");

    String json = mockMvc.perform(post("/api/dsl/promote?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"outcome\":\"unchanged\"");
    assertThat(json).contains("\"published\":0");
  }

  @Test
  void applyPromotesBundleAndVerifiesDigest() throws Exception {
    publish(devDir, "A", "v1");
    publish(devDir, "B", "v2");

    String json = mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"dryRun\":false");
    assertThat(json).contains("\"published\":2");
    assertThat(json).contains("\"outcome\":\"published\"");
    assertThat(stagingDir.resolve(".workbench/published/A.json")).exists();
    assertThat(stagingDir.resolve(".workbench/published/B.json")).exists();

    verify(auditService).record(any(), eq("PROMOTION"), eq("staging"), any(),
            eq("SUCCESS"), any());
    // promoted markers on the target must re-export with a matching digest
    DslDefinitionBundleService verifier = new DslDefinitionBundleService(mapper,
            Optional.empty(), DslProperties.bundleServiceDefaults());
    var bundle = verifier.export(devDir, false);
    verifier.verifyApplied(stagingDir, bundle);
  }

  @Test
  void applyWithSelectionPromotesOnlySelectedDefinitions() throws Exception {
    publish(devDir, "A", "v1");
    publish(devDir, "B", "v2");

    String json = mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\","
                    + "\"definitions\":[\"B\"]}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(json).contains("\"published\":1");
    assertThat(stagingDir.resolve(".workbench/published/B.json")).exists();
    assertThat(stagingDir.resolve(".workbench/published/A.json")).doesNotExist();
  }

  @Test
  void applyRecordsAuditWithSourceTargetAndDefinitions() throws Exception {
    publish(devDir, "A", "v1");

    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<Object> details = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(auditService).record(any(), eq("PROMOTION"), eq("staging"), any(),
            eq("SUCCESS"), details.capture());
    String detailsJson = mapper.writeValueAsString(details.getValue());
    assertThat(detailsJson).contains("\"source\":\"dev\"");
    assertThat(detailsJson).contains("\"target\":\"staging\"");
    assertThat(detailsJson).contains("A");
  }

  @Test
  void sameSourceAndTargetReturns400() throws Exception {
    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"dev\"}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void unknownTargetReturns404() throws Exception {
    publish(devDir, "A", "v1");

    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"prod\"}"))
            .andExpect(status().isNotFound())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("ENV_NOT_FOUND"));
    verifyNoInteractions(auditService);
  }

  @Test
  void missingSourceDirReturns404() throws Exception {
    DslProperties props = DslProperties.builder()
            .sourceDir(devDir.toString())
            .promotion(new DslProperties.Promotion(Map.of(
                    "dev", new DslProperties.Promotion.Environment(devDir.toString()),
                    "broken", new DslProperties.Promotion.Environment(
                            devDir.resolve("does-not-exist").toString()))))
            .build();
    DslDefinitionBundleService bundleService = new DslDefinitionBundleService(mapper,
            Optional.empty(), DslProperties.bundleServiceDefaults());
    DslPromoteHandler handler = new DslPromoteHandler(props, bundleService,
            new DslDefinitionHistoryService(props, mapper), mapper, auditProvider());
    MockMvc brokenMvc = mockMvcFor(handler);

    brokenMvc.perform(post("/api/dsl/promote?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"broken\"}"))
            .andExpect(status().isNotFound());
  }

  @SuppressWarnings("unchecked")
  private org.springframework.beans.factory.ObjectProvider<DslAuditService> auditProvider() {
    org.springframework.beans.factory.ObjectProvider<DslAuditService> provider = mock(
            org.springframework.beans.factory.ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(auditService);
    return provider;
  }

  private void publish(Path root, String name, String version) throws Exception {
    Path dir = root.resolve(".workbench/published");
    Files.createDirectories(dir);
    DraftRequest req = new DraftRequest(name, "process", "Published", version, "q", null, null);
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
    try (var stream = Files.walk(path)) {
      List<Path> paths = stream.sorted((a, b) -> b.compareTo(a)).toList();
      for (Path p : paths) {
        Files.deleteIfExists(p);
      }
    }
  }
}
