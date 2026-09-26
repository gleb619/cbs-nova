package cbs.nova.starter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.router.ChangeRequestRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import cbs.nova.starter.exception.ChangeRequestException;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.ChangeRequestService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit characterization spec for {@link ChangeRequestHandler}: submit/list/approve/reject HTTP
 * shell around a mocked {@link ChangeRequestService}; role resolution exercised through the shared
 * {@link RoleResolver}.
 */
class ChangeRequestHandlerTest {

  private ChangeRequestService changeRequestService;
  private RoleResolver roleResolver;
  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    changeRequestService = mock(ChangeRequestService.class);
    roleResolver = mock(RoleResolver.class);

    ChangeRequestHandler handler = new ChangeRequestHandler(changeRequestService, roleResolver,
            objectMapper);
    ChangeRequestRouterConfiguration router = new ChangeRequestRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.changeRequestRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void submitReturns201Envelope() throws Exception {
    asActor("alice", Role.AUTHOR);
    ChangeRequestEntity created = new ChangeRequestEntity(7L, "LoanA", "{}", "alice",
            Instant.now(), Status.PENDING, null, null, null);
    when(changeRequestService.submit(eq("LoanA"), eq("alice"), any()))
            .thenReturn(created);

    mockMvc.perform(post("/api/dsl/drafts/LoanA/change-request")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.definitionName").value("LoanA"))
            .andExpect(jsonPath("$.status").value("PENDING"));

    verify(changeRequestService).submit(eq("LoanA"), eq("alice"), any());
  }

  @Test
  void submitReturns404WhenNoDraft() throws Exception {
    asActor("alice", Role.AUTHOR);
    when(changeRequestService.submit(eq("Missing"), any(), any()))
            .thenThrow(new ChangeRequestException("NOT_FOUND", "No draft found for: Missing"));

    mockMvc.perform(post("/api/dsl/drafts/Missing/change-request")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No draft found for: Missing"))
            .andExpect(jsonPath("$.entityName").value("Missing"));
  }

  @Test
  void listReturnsFilteredItems() throws Exception {
    asActor("alice", Role.VIEWER);
    ChangeRequestEntity pending = new ChangeRequestEntity(1L, "LoanA", "{}", "alice",
            Instant.now(), Status.PENDING, null, null, null);
    when(changeRequestService.list("LoanA", Status.PENDING)).thenReturn(List.of(pending));

    mockMvc.perform(get("/api/dsl/change-requests")
            .param("definitionName", "LoanA")
            .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].definitionName").value("LoanA"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));

    verify(changeRequestService).list("LoanA", Status.PENDING);
  }

  @Test
  void listOmitsBlankFilters() throws Exception {
    asActor("alice", Role.VIEWER);
    when(changeRequestService.list(isNull(), isNull())).thenReturn(List.of());

    mockMvc.perform(get("/api/dsl/change-requests")
            .param("definitionName", "   ")
            .param("status", "  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

    verify(changeRequestService).list(isNull(), isNull());
  }

  @Test
  void listReturnsNewestFirstPassthrough() throws Exception {
    asActor("alice", Role.VIEWER);
    ChangeRequestEntity first = new ChangeRequestEntity(2L, "LoanA", "{}", "bob",
            Instant.now(), Status.APPROVED, "carol", Instant.now(), "ok");
    ChangeRequestEntity second = new ChangeRequestEntity(1L, "LoanA", "{}", "alice",
            Instant.now().minusSeconds(10), Status.PENDING, null, null, null);
    when(changeRequestService.list(isNull(), isNull())).thenReturn(List.of(first, second));

    mockMvc.perform(get("/api/dsl/change-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(2))
            .andExpect(jsonPath("$[1].id").value(1));
  }

  @Test
  void listInvalidStatusReturns400Envelope() throws Exception {
    asActor("alice", Role.VIEWER);

    mockMvc.perform(get("/api/dsl/change-requests").param("status", "nope"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(
                    "Invalid value for query parameter 'status': 'nope' (expected one of PENDING, APPROVED, REJECTED, SUPERSEDED)"));
  }

  @Test
  void approveReturns200WhenAllowed() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.approve(eq(7L), eq("bob"), eq(Role.AUTHOR), isNull(), any()))
            .thenReturn(ServerResponse.ok().body(Map.of("published", true)));

    mockMvc.perform(post("/api/dsl/change-requests/7/approve")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(true));

    verify(roleResolver).resolve(any(HttpServletRequest.class));
    verify(changeRequestService).approve(eq(7L), eq("bob"), eq(Role.AUTHOR), isNull(), any());
  }

  @Test
  void approvePassesCommentFromBody() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.approve(eq(7L), eq("bob"), eq(Role.AUTHOR), eq("looks good"), any()))
            .thenReturn(ServerResponse.ok().body(Map.of("published", true)));

    mockMvc.perform(post("/api/dsl/change-requests/7/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"comment\":\"looks good\"}"))
            .andExpect(status().isOk());

    verify(changeRequestService).approve(eq(7L), eq("bob"), eq(Role.AUTHOR), eq("looks good"),
            any());
  }

  @Test
  void approveReturns403OnSelfApproval() throws Exception {
    asActor("alice", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.approve(eq(7L), eq("alice"), eq(Role.AUTHOR), isNull(), any()))
            .thenThrow(new ChangeRequestException("FORBIDDEN",
                    "Change request requester cannot approve or reject their own request"));

    mockMvc.perform(post("/api/dsl/change-requests/7/approve")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void approveReturns404WhenNotFound() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.approve(eq(99L), eq("bob"), eq(Role.AUTHOR), isNull(), any()))
            .thenThrow(new ChangeRequestException("NOT_FOUND", "No change request: 99"));

    mockMvc.perform(post("/api/dsl/change-requests/99/approve")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No change request: 99"));
  }

  @Test
  void approveReturns409WhenNotPending() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.approve(eq(7L), eq("bob"), eq(Role.AUTHOR), isNull(), any()))
            .thenThrow(new ChangeRequestException("CONFLICT",
                    "Change request 7 is APPROVED and can no longer be decided"));

    mockMvc.perform(post("/api/dsl/change-requests/7/approve")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void rejectReturns200WithRejectedEntity() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    ChangeRequestEntity rejected = new ChangeRequestEntity(7L, "LoanA", "{}", "alice",
            Instant.now(), Status.REJECTED, null, null, "not now");
    when(changeRequestService.reject(eq(7L), eq("bob"), eq(Role.AUTHOR), eq("not now"), any()))
            .thenReturn(rejected);

    mockMvc.perform(post("/api/dsl/change-requests/7/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"comment\":\"not now\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.comment").value("not now"));
  }

  @Test
  void rejectReturns403OnSelfRejection() throws Exception {
    asActor("alice", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.reject(eq(7L), eq("alice"), eq(Role.AUTHOR), eq("no"), any()))
            .thenThrow(new ChangeRequestException("FORBIDDEN",
                    "Change request requester cannot approve or reject their own request"));

    mockMvc.perform(post("/api/dsl/change-requests/7/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"comment\":\"no\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void rejectReturns404WhenNotFound() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);
    when(changeRequestService.reject(eq(99L), eq("bob"), eq(Role.AUTHOR), isNull(), any()))
            .thenThrow(new ChangeRequestException("NOT_FOUND", "No change request: 99"));

    mockMvc.perform(post("/api/dsl/change-requests/99/reject")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No change request: 99"));
  }

  @Test
  void rejectMalformedJsonReturns400Envelope() throws Exception {
    asActor("bob", Role.AUTHOR);
    when(roleResolver.resolve(any(HttpServletRequest.class))).thenReturn(Role.AUTHOR);

    mockMvc.perform(post("/api/dsl/change-requests/7/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  private void asActor(String name, Role role) {
    SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken(name, "", "ROLE_" + role.name()));
  }
}
