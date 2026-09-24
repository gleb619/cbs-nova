package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.config.router.DslPromoteRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionBundleService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Handler-spec for {@link DslPromoteHandler} (T627): envelope shapes and error/failure mapping that
 * {@code DslPromoteResourceTest} does not pin — sorted env/definition envelopes, dry-run diff count
 * mapping, validation/parse failure envelopes, apply count mapping, and the apply-failure audit +
 * propagation path. Downstream services are mocked; routing and exception mapping are exercised
 * through MockMvc like the sibling handler specs.
 */
class DslPromoteHandlerTest {

  private MockMvc mockMvc;
  private Path devDir;
  private Path stagingDir;
  private DslDefinitionBundleService bundleService;
  private DslAuditService auditService;

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    devDir = Files.createTempDirectory("dsl-promote-handler-dev-");
    stagingDir = Files.createTempDirectory("dsl-promote-handler-staging-");
    DslProperties props = DslProperties.builder()
            .sourceDir(devDir.toString())
            .promotion(new DslProperties.Promotion(Map.of(
                    "dev", new DslProperties.Promotion.Environment(devDir.toString()),
                    "staging", new DslProperties.Promotion.Environment(stagingDir.toString()))))
            .build();
    bundleService = mock(DslDefinitionBundleService.class);
    auditService = mock(DslAuditService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslAuditService> auditProvider = mock(ObjectProvider.class);
    when(auditProvider.getIfAvailable()).thenReturn(auditService);
    DslPromoteHandler handler = new DslPromoteHandler(props, bundleService,
            mapper, auditProvider);

    DslPromoteRouterConfiguration router = new DslPromoteRouterConfiguration();
    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();
    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslPromoteRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    deleteRecursively(devDir);
    deleteRecursively(stagingDir);
  }

  @Test
  void environmentsReturnsSortedNameOnlyEnvelope() throws Exception {
    mockMvc.perform(get("/api/dsl/promote/environments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("dev"))
            .andExpect(jsonPath("$[1].name").value("staging"));
  }

  @Test
  void definitionsReturnsSortedEnvelopeWithTypeAndStatus() throws Exception {
    when(bundleService.export(any(Path.class), eq(false)))
            .thenReturn(bundle(entry("B", "workflow"), entry("A", "process")));

    mockMvc.perform(get("/api/dsl/promote/definitions").param("env", "dev"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("A"))
            .andExpect(jsonPath("$[0].type").value("process"))
            .andExpect(jsonPath("$[0].status").value("Published"))
            .andExpect(jsonPath("$[1].name").value("B"))
            .andExpect(jsonPath("$[1].type").value("workflow"));

    verify(bundleService).export(eq(devDir), eq(false));
  }

  @Test
  void definitionsWithoutEnvParamReturnsEnvNotFoundEnvelope() throws Exception {
    // characterization: the missing param is reported in the ErrorResponse entityName slot
    mockMvc.perform(get("/api/dsl/promote/definitions"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ENV_NOT_FOUND"))
            .andExpect(jsonPath("$.entityName").value("env"));

    verifyNoInteractions(bundleService);
  }

  @Test
  void dryRunMapsDiffOutcomesIntoCountsAndEntryResults() throws Exception {
    when(bundleService.exportSelected(any(Path.class), nullable(Boolean.class), any()))
            .thenReturn(bundle(entry("A", "process"), entry("B", "process")));
    when(bundleService.diffForImport(any(Path.class), any())).thenReturn(List.of(
            new ImportEntryResult("A", "created", null),
            new ImportEntryResult("B", "updated", null),
            new ImportEntryResult("C", "skipped", "target missing draft base"),
            new ImportEntryResult("D", "unchanged", null),
            new ImportEntryResult("E", "created", null)));

    mockMvc.perform(post("/api/dsl/promote?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.reloaded").value(false))
            .andExpect(jsonPath("$.published").value(3))
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.results.length()").value(5))
            .andExpect(jsonPath("$.results[2].name").value("C"))
            .andExpect(jsonPath("$.results[2].outcome").value("skipped"))
            .andExpect(jsonPath("$.results[2].message").value("target missing draft base"));

    verify(bundleService, never()).applyToTarget(any(Path.class), any());
    verify(bundleService, never()).verifyApplied(any(Path.class), any());
    verifyNoInteractions(auditService);
  }

  @Test
  void dryRunBundleValidationFailureReturnsBadRequestEnvelope() throws Exception {
    when(bundleService.exportSelected(any(Path.class), nullable(Boolean.class), any()))
            .thenReturn(bundle(entry("A", "process")));
    org.mockito.Mockito.doThrow(new IllegalArgumentException("digest mismatch: abc"))
            .when(bundleService).validateForImport(any(DefinitionBundle.class));

    mockMvc.perform(post("/api/dsl/promote?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("digest mismatch: abc"));

    verify(bundleService, never()).diffForImport(any(Path.class), any());
    verifyNoInteractions(auditService);
  }

  @Test
  void malformedBodyReturnsInvalidRequestEnvelope() throws Exception {
    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("malformed promotion request JSON"));

    verifyNoInteractions(bundleService, auditService);
  }

  @Test
  void missingTargetReturnsInvalidRequestEnvelope() throws Exception {
    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("source and target are required"));

    verifyNoInteractions(bundleService, auditService);
  }

  @Test
  void applyMapsPublishedAndFailedCountsFromEntryOutcomes() throws Exception {
    when(bundleService.exportSelected(any(Path.class), nullable(Boolean.class), any()))
            .thenReturn(bundle(entry("A", "process"), entry("B", "process")));
    when(bundleService.applyToTarget(any(Path.class), any())).thenReturn(List.of(
            new ImportEntryResult("A", "published", null),
            new ImportEntryResult("B", "conflict", "target version is newer"),
            new ImportEntryResult("C", "published", null)));

    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(false))
            .andExpect(jsonPath("$.reloaded").value(false))
            .andExpect(jsonPath("$.published").value(2))
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.results.length()").value(3));

    verify(bundleService).verifyApplied(eq(stagingDir), any(DefinitionBundle.class));
    verify(auditService).record(any(), eq("PROMOTION"), eq("staging"), any(),
            eq("SUCCESS"), any());
  }

  @Test
  void applyFailureAuditsFailureAndPropagatesAsInternalError() throws Exception {
    when(bundleService.exportSelected(any(Path.class), nullable(Boolean.class), any()))
            .thenReturn(bundle(entry("A", "process")));
    when(bundleService.applyToTarget(any(Path.class), any()))
            .thenThrow(new RuntimeException("disk full"));

    mockMvc.perform(post("/api/dsl/promote")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"dev\",\"target\":\"staging\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

    ArgumentCaptor<Object> details = ArgumentCaptor.forClass(Object.class);
    verify(auditService).record(any(), eq("PROMOTION"), eq("staging"), any(),
            eq("FAILURE"), details.capture());
    String detailsJson = mapper.writeValueAsString(details.getValue());
    assertThat(detailsJson).contains("\"source\":\"dev\"");
    assertThat(detailsJson).contains("\"target\":\"staging\"");
    assertThat(detailsJson).contains("\"error\":\"disk full\"");
    verify(auditService, never()).record(any(), eq("PROMOTION"), eq("staging"), any(),
            eq("SUCCESS"), any());
  }

  private DefinitionBundle bundle(DefinitionBundleEntry... entries) {
    return new DefinitionBundle(1, "engine", "2026-01-01T00:00:00Z", List.of(entries), "digest-x");
  }

  private DefinitionBundleEntry entry(String name, String type) {
    return new DefinitionBundleEntry(
            new DraftRequest(name, type, "Published", "v1", "q", null, null), "dsl");
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
