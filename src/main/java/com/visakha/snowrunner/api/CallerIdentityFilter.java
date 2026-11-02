package com.visakha.snowrunner.api;

import com.visakha.snowrunner.api.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces the two roles on every endpoint. Liveness stays open — the scheduler checks it before
 * authentication exists in the picture, and it reveals nothing but "process up". Every other
 * actuator endpoint needs an authenticated caller; every POST under /api/v1 is a control act and
 * needs the scheduler role; GETs need any authenticated role.
 */
@Component
public class CallerIdentityFilter extends OncePerRequestFilter {

  static final String CALLER_ATTRIBUTE = "callerIdentity";

  private final CallerIdentityResolver resolver;
  private final ObjectMapper mapper;

  /** Creates the filter around the resolver. */
  public CallerIdentityFilter(CallerIdentityResolver resolver, ObjectMapper mapper) {
    this.resolver = resolver;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path.equals("/actuator/health/liveness")) {
      chain.doFilter(request, response);
      return;
    }
    boolean protectedPath = path.startsWith("/api/v1/") || path.startsWith("/actuator/");
    if (!protectedPath) {
      chain.doFilter(request, response);
      return;
    }
    var caller = resolver.resolve(request);
    if (caller.isEmpty()) {
      reject(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No or invalid credentials.");
      return;
    }
    if (path.startsWith("/api/v1/")
        && request.getMethod().equals("POST")
        && caller.get().role() != CallerRole.SCHEDULER) {
      reject(
          response,
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Support callers cannot trigger, resume, pause, cancel, or dry-run.");
      return;
    }
    request.setAttribute(CALLER_ATTRIBUTE, caller.get());
    chain.doFilter(request, response);
  }

  private void reject(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(response.getWriter(), ErrorResponse.of(code, message, null, null, null));
  }
}
