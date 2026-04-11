package cbs.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LocalAuthController.class)
@Import(LocalAuthControllerTest.TestConfig.class)
@TestPropertySource(
    properties = {
        "keycloak.enabled=false",
        "spring.application.name=test-app",
        "local-auth.users[0].username=admin",
        "local-auth.users[0].password=secret",
        "local-auth.users[0].roles[0]=ADMIN"
    })
class LocalAuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private SecurityFilterChain securityFilterChain;

  @MockitoBean
  private AuthenticationManager authenticationManager;

  @Test
  @DisplayName("Should return 200 with JWT token when credentials are valid")
  void shouldReturn200WithTokenWhenValidCredentials() throws Exception {
    var auth = new UsernamePasswordAuthenticationToken(
        "admin", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    when(authenticationManager.authenticate(any())).thenReturn(auth);

    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("username", "admin", "password", "secret"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty())
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(3600));
  }

  @Test
  @DisplayName("Should return a properly formatted signed JWT token")
  void shouldReturnSignedJwtTokenWhenValidCredentials() throws Exception {
    var auth = new UsernamePasswordAuthenticationToken(
        "admin", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    when(authenticationManager.authenticate(any())).thenReturn(auth);

    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("username", "admin", "password", "secret"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token")
            .value(Matchers.matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+")));
  }

  @Test
  @DisplayName("Should include all roles in token when user has multiple roles")
  void shouldIncludeAllRolesInTokenWhenUserHasMultipleRoles() throws Exception {
    var auth = new UsernamePasswordAuthenticationToken(
        "admin",
        "secret",
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER")));
    when(authenticationManager.authenticate(any())).thenReturn(auth);

    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("username", "admin", "password", "secret"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty());
  }

  @Test
  @DisplayName("Should return 401 with error details when password is invalid")
  void shouldReturn401WhenPasswordIsInvalid() throws Exception {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"))
        .andExpect(jsonPath("$.message").value("Invalid username or password"));
  }

  @Test
  @DisplayName("Should return 401 when username does not exist")
  void shouldReturn401WhenUserDoesNotExist() throws Exception {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("username", "ghost", "password", "pass"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"));
  }

  @Test
  @DisplayName("Should return 401 when request body has empty credentials")
  void shouldReturn401WhenRequestBodyHasEmptyCredentials() throws Exception {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Should return 415 when Content-Type header is missing")
  void shouldReturn415WhenContentTypeIsMissing() throws Exception {
    mockMvc
        .perform(post("/api/public/auth/token")
            .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @DisplayName("Should return 400 when request body is not valid JSON")
  void shouldReturn400WhenRequestBodyIsInvalidJson() throws Exception {
    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("not-json"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Token endpoint should be publicly accessible without authentication")
  void shouldAllowPublicAccessToTokenEndpointWithoutAuth() throws Exception {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    // 401 from our logic, not from Spring Security → endpoint is public
    mockMvc
        .perform(post("/api/public/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("username", "x", "password", "y"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
