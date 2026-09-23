package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.model.PreCheck;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.PieceCheckBlockRegistry;
import cbs.nova.starter.service.PieceCheckPipeline;
import cbs.nova.starter.service.PieceManifestService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Behaviour matrix for {@link PieceGuardFilter}: opt-in pass-through, role / feature-flag /
 * rate-class denial with the unified {@link ErrorResponse} envelope, {@code audit-only} fail mode,
 * and success-path request attributes.
 */
class PieceGuardFilterTest {

  private static final String PIECE_ID = "dsl-reload";
  private static final String ROUTE = "/api/dsl/reload";

  private ObjectMapper objectMapper;
  private PieceManifestService manifestService;
  private ObjectProvider<DslAuditService> auditServiceProvider;
  private DslAuditService auditService;
  private AtomicLong clock;
  private PieceGuardFilter filter;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().build();
    manifestService = mock(PieceManifestService.class);
    auditService = mock(DslAuditService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslAuditService> provider = mock(ObjectProvider.class);
    auditServiceProvider = provider;
    when(auditServiceProvider.getIfAvailable()).thenReturn(auditService);
    clock = new AtomicLong(1_000_000_000L);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // --- Opt-in guarantee -------------------------------------------------------

  @Test
  void routeAbsentFromManifestProceedsByteForByteUntouched() throws Exception {
    when(manifestService.findByRoute("GET", "/api/dsl/objects/search"))
            .thenReturn(Optional.empty());

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dsl/objects/search");
    Invocation invocation = invoke(request, chainWriting(200, "hello"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
    assertThat(invocation.response().getContentAsString()).isEqualTo("hello");
    assertThat(request.getAttribute(PieceGuardFilter.PIECE_ID_ATTRIBUTE)).isNull();
    assertThat(request.getAttribute(PieceGuardFilter.PRINCIPAL_ROLE_ATTRIBUTE)).isNull();
  }

  // --- role check --------------------------------------------------------------

  @Test
  void roleCheckPassesForJwtClaimedRoleAndAttributesAreSet() throws Exception {
    authenticateAs("caller", Role.AUTHOR);
    pieceWith(List.of(new PreCheck.RoleCheck(List.of("author", "operator"))));

    MockHttpServletRequest request = new MockHttpServletRequest("POST", ROUTE);
    Invocation invocation = invoke(request, chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
    assertThat(request.getAttribute(PieceGuardFilter.PIECE_ID_ATTRIBUTE)).isEqualTo(PIECE_ID);
    assertThat(request.getAttribute(PieceGuardFilter.PRINCIPAL_ROLE_ATTRIBUTE))
            .isEqualTo("AUTHOR");
  }

  @Test
  void roleCheckPassesForApiKeyPrincipalResolvingToAdmin() throws Exception {
    pieceWith(List.of(new PreCheck.RoleCheck(List.of("admin"))));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", ROUTE);
    request.addHeader(StarterConstants.API_KEY_HEADER, "secret-key");

    Invocation invocation = invoke(request, chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
    assertThat(request.getAttribute(PieceGuardFilter.PRINCIPAL_ROLE_ATTRIBUTE))
            .isEqualTo("ADMIN");
  }

  @Test
  void insufficientRoleIsDeniedWithUnifiedEnvelope() throws Exception {
    authenticateAs("caller", Role.RUNNER);
    pieceWith(List.of(new PreCheck.RoleCheck(List.of("operator"))));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isFalse();
    assertThat(invocation.response().getStatus()).isEqualTo(403);
    ErrorResponse body = decode(invocation.response());
    assertThat(body.getCode()).isEqualTo("FORBIDDEN");
    assertThat(body.getMessage()).contains("role").contains(PIECE_ID);
    assertThat(body.getContext())
            .containsEntry("pieceId", PIECE_ID)
            .containsEntry("check", "role");
  }

  // --- feature-flag check ------------------------------------------------------

  @Test
  void disabledFlagIsDeniedWithUnifiedEnvelope() throws Exception {
    pieceWith(List.of(new PreCheck.FeatureFlagCheck("workbench-publish")));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isFalse();
    assertThat(invocation.response().getStatus()).isEqualTo(403);
    ErrorResponse body = decode(invocation.response());
    assertThat(body.getCode()).isEqualTo("FORBIDDEN");
    assertThat(body.getContext())
            .containsEntry("pieceId", PIECE_ID)
            .containsEntry("check", "feature-flag");
  }

  @Test
  void enabledFlagFromPropertiesPasses() throws Exception {
    pieceWith(List.of(new PreCheck.FeatureFlagCheck("workbench-publish")));
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", Map.of("workbench-publish", true), null);
    filter = newFilter(properties, new PropertiesFeatureFlagSource(properties));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
  }

  @Test
  void flagCheckIsUnitTestableViaFeatureFlagSourceSeam() throws Exception {
    pieceWith(List.of(new PreCheck.FeatureFlagCheck("anything")));
    FeatureFlagSource seam = (flag, request) -> "anything".equals(flag);

    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"), seam);

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
  }

  // --- rate-class check ----------------------------------------------------------

  @Test
  void exhaustedRateClassIsDeniedWithUnifiedEnvelope() throws Exception {
    pieceWith(List.of(new PreCheck.RateClassCheck("control-plane")));
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", null,
            Map.of("control-plane", new CbsDslManifestProperties.RateClass(1, 0.5)));
    filter = newFilter(properties, new PropertiesFeatureFlagSource(properties));

    // capacity 1: first request consumes the only token; clock frozen → no refill.
    Invocation first = invoke(new MockHttpServletRequest("POST", ROUTE), chainWriting(200, "ok"));
    Invocation second = invoke(new MockHttpServletRequest("POST", ROUTE), chainWriting(200, "ok"));

    assertThat(first.chainRan()).isTrue();
    assertThat(second.chainRan()).isFalse();
    assertThat(second.response().getStatus()).isEqualTo(403);
    ErrorResponse body = decode(second.response());
    assertThat(body.getCode()).isEqualTo("FORBIDDEN");
    assertThat(body.getContext())
            .containsEntry("pieceId", PIECE_ID)
            .containsEntry("check", "rate-class");
  }

  @Test
  void refillAllowsRequestAfterElapsedTime() throws Exception {
    pieceWith(List.of(new PreCheck.RateClassCheck("control-plane")));
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", null,
            Map.of("control-plane", new CbsDslManifestProperties.RateClass(1, 1.0)));
    filter = newFilter(properties, new PropertiesFeatureFlagSource(properties));

    assertThat(invoke(new MockHttpServletRequest("POST", ROUTE), chainWriting(200, "ok"))
            .chainRan()).isTrue();
    // 1 token/sec capacity 1: after 1.1s one token has refilled.
    clock.addAndGet(1_100_000_000L);
    assertThat(invoke(new MockHttpServletRequest("POST", ROUTE), chainWriting(200, "ok"))
            .chainRan()).isTrue();
  }

  @Test
  void unconfiguredRateClassFailsClosed() throws Exception {
    pieceWith(List.of(new PreCheck.RateClassCheck("missing-class")));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isFalse();
    assertThat(invocation.response().getStatus()).isEqualTo(403);
    assertThat(decode(invocation.response()).getContext())
            .containsEntry("check", "rate-class");
  }

  @Test
  void rateClassBucketsArePerPrincipal() throws Exception {
    pieceWith(List.of(new PreCheck.RateClassCheck("control-plane")));
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", null,
            Map.of("control-plane", new CbsDslManifestProperties.RateClass(1, 0.5)));
    filter = newFilter(properties, new PropertiesFeatureFlagSource(properties));

    MockHttpServletRequest alice = new MockHttpServletRequest("POST", ROUTE);
    alice.setRemoteAddr("10.0.0.1");
    MockHttpServletRequest bob = new MockHttpServletRequest("POST", ROUTE);
    bob.setRemoteAddr("10.0.0.2");

    assertThat(invoke(alice, chainWriting(200, "ok")).chainRan()).isTrue();
    // Alice's bucket is empty but Bob's is untouched.
    assertThat(invoke(bob, chainWriting(200, "ok")).chainRan()).isTrue();
  }

  // --- check ordering ------------------------------------------------------------

  @Test
  void firstFailingCheckIsReported() throws Exception {
    pieceWith(List.of(
            new PreCheck.FeatureFlagCheck("disabled-flag"),
            new PreCheck.RoleCheck(List.of("admin"))));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.response().getStatus()).isEqualTo(403);
    assertThat(decode(invocation.response()).getContext())
            .containsEntry("check", "feature-flag");
  }

  // --- failMode ------------------------------------------------------------------

  @Test
  void auditOnlyAllowsRequestAndWritesFailureAuditRow() throws Exception {
    pieceWith("audit-only", List.of(new PreCheck.RoleCheck(List.of("operator"))));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);

    ArgumentCaptor<Object> details = ArgumentCaptor.forClass(Object.class);
    verify(auditService).record(anyString(), eq(StarterConstants.ACTION_PIECE_GUARD_DENY),
            eq(PIECE_ID), any(), eq(StarterConstants.OUTCOME_FAILURE), details.capture());
    assertThat(details.getValue()).isInstanceOfSatisfying(Map.class,
            map -> assertThat(map).containsEntry("check", "role"));
  }

  @Test
  void auditOnlyStillAllowsWhenAuditServiceAbsent() throws Exception {
    when(auditServiceProvider.getIfAvailable()).thenReturn(null);
    pieceWith("audit-only", List.of(new PreCheck.RoleCheck(List.of("operator"))));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void denyModeWritesNoAuditRow() throws Exception {
    pieceWith(List.of(new PreCheck.RoleCheck(List.of("operator"))));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.response().getStatus()).isEqualTo(403);
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  // --- T550: post-check trigger + block consult ----------------------------------

  @Test
  void successfulExecutionTriggersPostCheckPipelineWithExecutionContext() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, null);
    authenticateAs("caller", Role.AUTHOR);
    pieceWithPostChecks(List.of(new PreCheck.RoleCheck(List.of("author"))),
            List.of(new PostCheck.AuditWriteCheck("DEFINITION_RELOAD")));

    MockHttpServletRequest request = new MockHttpServletRequest("POST", ROUTE);
    request.addHeader(StarterConstants.CORRELATION_ID_HEADER, "rid-42");
    Invocation invocation = invoke(request, chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    assertThat(invocation.response().getStatus()).isEqualTo(200);
    ArgumentCaptor<InvariantContext> context = ArgumentCaptor.forClass(InvariantContext.class);
    verify(pipeline).onSuccess(any(Piece.class), anyString(), anyString(), eq("rid-42"),
            context.capture());
    assertThat(context.getValue().pieceId()).isEqualTo(PIECE_ID);
    assertThat(context.getValue().principalRole()).isEqualTo("AUTHOR");
    assertThat(context.getValue().method()).isEqualTo("POST");
    assertThat(context.getValue().path()).isEqualTo(ROUTE);
    assertThat(context.getValue().status()).isEqualTo(200);
  }

  @Test
  void postChecksNeverRunOnFailedExecutionStatus() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, null);
    authenticateAs("caller", Role.AUTHOR);
    pieceWithPostChecks(List.of(new PreCheck.RoleCheck(List.of("author"))),
            List.of(new PostCheck.NotifyCheck("workbench")));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(500, "boom"));

    assertThat(invocation.response().getStatus()).isEqualTo(500);
    verify(pipeline, never()).onSuccess(any(), any(), any(), any(), any());
  }

  @Test
  void postChecksNeverRunOnHandlerException() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, null);
    authenticateAs("caller", Role.AUTHOR);
    pieceWithPostChecks(List.of(new PreCheck.RoleCheck(List.of("author"))),
            List.of(new PostCheck.NotifyCheck("workbench")));

    FilterChain throwingChain = (req, res) -> {
      throw new ServletException("handler exploded");
    };
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", ROUTE), response,
            throwingChain)).isInstanceOf(ServletException.class);
    verify(pipeline, never()).onSuccess(any(), any(), any(), any(), any());
  }

  @Test
  void postChecksNeverRunOnPreCheckDenial() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, null);
    authenticateAs("caller", Role.RUNNER);
    pieceWithPostChecks(List.of(new PreCheck.RoleCheck(List.of("operator"))),
            List.of(new PostCheck.NotifyCheck("workbench")));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.response().getStatus()).isEqualTo(403);
    verify(pipeline, never()).onSuccess(any(), any(), any(), any(), any());
  }

  @Test
  void postChecksNeverRunForRoutesAbsentFromTheManifest() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, null);
    when(manifestService.findByRoute("GET", "/api/dsl/objects/search"))
            .thenReturn(Optional.empty());

    invoke(new MockHttpServletRequest("GET", "/api/dsl/objects/search"),
            chainWriting(200, "hello"));

    verify(pipeline, never()).onSuccess(any(), any(), any(), any(), any());
  }

  @Test
  void blockedPieceIsDeniedWithPieceBlockedCodeBeforePreChecks() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    PieceCheckBlockRegistry registry = mock(PieceCheckBlockRegistry.class);
    when(registry.isBlocked(PIECE_ID, "auth:caller")).thenReturn(true);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, registry);
    // Pre-check would pass — the block alone must deny.
    authenticateAs("caller", Role.ADMIN);
    pieceWithPostChecks(List.of(new PreCheck.RoleCheck(List.of("admin"))),
            List.of(new PostCheck.NotifyCheck("workbench")));

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isFalse();
    assertThat(invocation.response().getStatus()).isEqualTo(403);
    ErrorResponse body = decode(invocation.response());
    assertThat(body.getCode()).isEqualTo(StarterConstants.PIECE_BLOCKED_CODE);
    assertThat(body.getContext())
            .containsEntry("pieceId", PIECE_ID)
            .containsEntry("check", "post-check-block");
    verify(pipeline, never()).onSuccess(any(), any(), any(), any(), any());
  }

  @Test
  void unblockedPieceProceedsThroughPreChecksNormally() throws Exception {
    PieceCheckPipeline pipeline = mock(PieceCheckPipeline.class);
    PieceCheckBlockRegistry registry = mock(PieceCheckBlockRegistry.class);
    when(registry.isBlocked(anyString(), anyString())).thenReturn(false);
    filter = newFilter(new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            new PropertiesFeatureFlagSource(
                    new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml")),
            pipeline, registry);
    authenticateAs("caller", Role.AUTHOR);
    pieceWithPostChecks(List.of(new PreCheck.RoleCheck(List.of("author"))), List.of());

    Invocation invocation = invoke(new MockHttpServletRequest("POST", ROUTE),
            chainWriting(200, "ok"));

    assertThat(invocation.chainRan()).isTrue();
    verify(registry).isBlocked(PIECE_ID, "auth:caller");
    // No postCheck entries → no pipeline invocation.
    verify(pipeline, never()).onSuccess(any(), any(), any(), any(), any());
  }

  // --- helpers ----------------------------------------------------------------------

  private PieceGuardFilter newFilter(CbsDslManifestProperties properties,
          FeatureFlagSource flagSource) {
    return newFilter(properties, flagSource, null, null);
  }

  private PieceGuardFilter newFilter(CbsDslManifestProperties properties,
          FeatureFlagSource flagSource, PieceCheckPipeline postCheckPipeline,
          PieceCheckBlockRegistry blockRegistry) {
    return new PieceGuardFilter(manifestService,
            new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME), flagSource, properties,
            auditServiceProvider, objectMapper, clock::get, postCheckPipeline, blockRegistry);
  }

  private void pieceWithPostChecks(List<PreCheck> preChecks, List<PostCheck> postChecks) {
    Piece piece = new Piece(PIECE_ID,
            new Target.ApiTarget("POST " + ROUTE), preChecks, postChecks, "deny");
    when(manifestService.findByRoute(eq("POST"), eq(ROUTE))).thenReturn(Optional.of(piece));
  }

  private void pieceWith(List<PreCheck> preChecks) {
    pieceWith("deny", preChecks);
  }

  private void pieceWith(String failMode, List<PreCheck> preChecks) {
    Piece piece = new Piece(PIECE_ID,
            new Target.ApiTarget("POST " + ROUTE), preChecks, List.of(), failMode);
    when(manifestService.findByRoute(eq("POST"), eq(ROUTE))).thenReturn(Optional.of(piece));
  }

  private Invocation invoke(MockHttpServletRequest request, FilterChain chain) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return new Invocation(chain instanceof RecordingChain rc && rc.ran.get(), response);
  }

  private static FilterChain chainWriting(int status, String body) {
    return new RecordingChain(status, body);
  }

  private static final class RecordingChain implements FilterChain {

    private final int status;
    private final String body;
    private final AtomicBoolean ran = new AtomicBoolean();

    private RecordingChain(int status, String body) {
      this.status = status;
      this.body = body;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
            jakarta.servlet.ServletResponse response) throws IOException {
      ran.set(true);
      ((jakarta.servlet.http.HttpServletResponse) response).setStatus(status);
      ((jakarta.servlet.http.HttpServletResponse) response).getWriter().write(body);
    }
  }

  private record Invocation(boolean chainRan, MockHttpServletResponse response) {
  }

  private ErrorResponse decode(MockHttpServletResponse response) throws IOException {
    return objectMapper.readValue(response.getContentAsByteArray(), ErrorResponse.class);
  }

  private static void authenticateAs(String principal, Role role) {
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                    principal,
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }
}
