package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RunIdentityResolverTest {

  private final RunIdentityResolver resolver = new RunIdentityResolver();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void authenticatedNonAnonymousUserReturnsPrincipalName() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getName()).thenReturn("alice");
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(resolver.resolve()).isEqualTo("alice");
  }

  @Test
  void anonymousAuthenticationReturnsNull() {
    Authentication auth = new AnonymousAuthenticationToken(
            "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(resolver.resolve()).isNull();
  }

  @Test
  void noSecurityContextReturnsNull() {
    SecurityContextHolder.clearContext();

    assertThat(resolver.resolve()).isNull();
  }

  @Test
  void xApiKeyHeaderReturnsApiKeyLabel() {
    SecurityContextHolder.clearContext();
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Api-Key")).thenReturn("secret-token");
    ServletRequestAttributes attrs = new ServletRequestAttributes(request);
    RequestContextHolder.setRequestAttributes(attrs);

    assertThat(resolver.resolve()).isEqualTo("api-key");
  }

  @Test
  void blankXApiKeyHeaderReturnsNull() {
    SecurityContextHolder.clearContext();
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Api-Key")).thenReturn("   ");
    ServletRequestAttributes attrs = new ServletRequestAttributes(request);
    RequestContextHolder.setRequestAttributes(attrs);

    assertThat(resolver.resolve()).isNull();
  }

  @Test
  void noRequestContextReturnsNull() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();

    assertThat(resolver.resolve()).isNull();
  }
}
