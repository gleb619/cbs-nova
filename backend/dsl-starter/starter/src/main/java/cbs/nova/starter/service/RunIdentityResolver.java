package cbs.nova.starter.service;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RunIdentityResolver {

  public static final String SYSTEM = "system";

  public @Nullable String resolve() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      return auth.getName();
    }
    String apiKey = currentApiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      return "api-key";
    }
    return null;
  }

  private @Nullable String currentApiKey() {
    try {
      if (RequestContextHolder
              .currentRequestAttributes() instanceof ServletRequestAttributes attrs) {
        return attrs.getRequest().getHeader("X-Api-Key");
      }
    } catch (IllegalStateException ignored) {
      // No request context in async/scheduled threads.
    }
    return null;
  }
}
