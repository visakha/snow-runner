package com.visakha.snowrunner.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PLACEHOLDER, NOT PRODUCTION AUTH. Resolves identity from X-Caller-Id and X-Caller-Role headers so
 * local runs and tests can exercise the two roles. Entra ID app roles must replace this before
 * production: a header any client can send proves nothing, and the 007 auth decision is still open.
 * When the real mechanism lands, it implements CallerIdentityResolver and this class is deleted —
 * no controller changes.
 */
@Component
public class HeaderCallerIdentityResolver implements CallerIdentityResolver {

  @Override
  public Optional<CallerIdentity> resolve(HttpServletRequest request) {
    String id = request.getHeader("X-Caller-Id");
    String role = request.getHeader("X-Caller-Role");
    if (id == null || id.isBlank() || role == null || role.isBlank()) {
      return Optional.empty();
    }
    CallerRole parsed =
        switch (role.trim().toLowerCase()) {
          case "scheduler" -> CallerRole.SCHEDULER;
          case "support" -> CallerRole.SUPPORT;
          default -> null;
        };
    if (parsed == null) {
      return Optional.empty();
    }
    return Optional.of(new CallerIdentity(id.trim(), parsed));
  }
}
