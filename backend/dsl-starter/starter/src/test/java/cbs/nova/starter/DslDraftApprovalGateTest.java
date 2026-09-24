package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import cbs.nova.starter.controller.DslReloadHandler;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * T568 approval gate on {@link DslDraftHandler#publish}: with
 * {@code cbs.dsl.approval.required=true} a caller below {@link Role#OPERATOR} is rejected with 403
 * (audited FAILURE, nothing published) while OPERATOR/ADMIN pass; with the flag off (default)
 * behaviour is unchanged.
 */
class DslDraftApprovalGateTest {

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new InputStreamHttpMessageConverter(), new StringBodyHttpMessageConverter());

  private final ObjectMapper mapper = new ObjectMapper();
  private Path sourceDir;

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-approval-gate-test-");
  }

  @AfterEach
  void tearDown() throws IOException {
    SecurityContextHolder.clearContext();
    if (sourceDir != null && Files.exists(sourceDir)) {
      try (var stream = Files.walk(sourceDir)) {
        var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
        for (Path p : paths) {
          Files.deleteIfExists(p);
        }
      }
    }
  }

  @Test
  void authorIsDeniedWhenApprovalRequiredAndFailureIsAudited() throws Exception {
    var audit = AuditTestSupport.h2();
    DslDraftHandler handler = handler(gatedProps(true), audit);
    authenticateAs("author", Role.AUTHOR);

    ServerResponse response = handler.publish(postPublishRequest("LoanA"));

    assertThat(response.statusCode().value()).isEqualTo(403);
    ErrorResponse body = (ErrorResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.getCode()).isEqualTo(StarterConstants.FORBIDDEN_CODE);
    assertThat(body.getMessage()).isEqualTo("publish requires approval");
    assertThat(sourceDir.resolve(".workbench/published/LoanA.json")).doesNotExist();

    var rows = audit.service().search(null, 0, 10);
    assertThat(rows.total()).isEqualTo(1);
    var row = rows.items().get(0);
    assertThat(row.action()).isEqualTo("DEFINITION_PUBLISH");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("LoanA");
  }

  @ParameterizedTest
  @EnumSource(names = {"OPERATOR", "ADMIN"})
  void operatorAndAboveAreExemptFromTheGate(Role role) throws Exception {
    DslDraftHandler handler = handler(gatedProps(true), AuditTestSupport.h2());
    authenticateAs("operator", role);

    ServerResponse response = handler.publish(postPublishRequest("LoanA"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(sourceDir.resolve(".workbench/published/LoanA.json")).exists();
  }

  @Test
  void authorPassesWhenApprovalNotRequired() throws Exception {
    DslDraftHandler handler = handler(gatedProps(false), AuditTestSupport.h2());
    authenticateAs("author", Role.AUTHOR);

    ServerResponse response = handler.publish(postPublishRequest("LoanA"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(sourceDir.resolve(".workbench/published/LoanA.json")).exists();
  }

  private DslProperties gatedProps(boolean required) {
    return DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .approval(new DslProperties.Approval(required))
            .build();
  }

  private DslDraftHandler handler(DslProperties props, AuditTestSupport.Harness audit) {
    return new DslDraftHandler(props,
            new DslReloadHandler(props, null, null, null, null, null, null, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            AuditTestSupport.providerOf(audit.service()), null, null, null,
            roleResolverProvider(), null, null);
  }

  private static ObjectProvider<RoleResolver> roleResolverProvider() {
    return new ObjectProvider<>() {
      @Override
      public RoleResolver getObject() {
        return resolver();
      }

      @Override
      public RoleResolver getIfAvailable() {
        return resolver();
      }

      @Override
      public RoleResolver getIfUnique() {
        return resolver();
      }

      private RoleResolver resolver() {
        return new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME);
      }
    };
  }

  private static ServerRequest postPublishRequest(String name) {
    var req = new MockHttpServletRequest("POST", "/api/dsl/drafts/" + name + "/publish");
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType(MediaType.APPLICATION_JSON_VALUE);
    req.setContent(("{\"name\":\"" + name
            + "\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"1\"}").getBytes());
    return ServerRequest.create(req, CONVERTERS);
  }

  private static void authenticateAs(String principal, Role role) {
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                    principal,
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
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

  private static final class StringBodyHttpMessageConverter
          implements
            HttpMessageConverter<String> {

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return String.class.isAssignableFrom(clazz);
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
    public String read(Class<? extends String> clazz, HttpInputMessage inputMessage)
            throws IOException {
      return new String(inputMessage.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void write(String string, MediaType contentType, HttpOutputMessage outputMessage) {
      throw new UnsupportedOperationException();
    }
  }
}
