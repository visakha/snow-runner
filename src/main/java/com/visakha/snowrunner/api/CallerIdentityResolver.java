package com.visakha.snowrunner.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** Resolves the caller from a request, or empty when the request proves nobody. */
public interface CallerIdentityResolver {
  /** Resolves the caller, or empty when unauthenticated. */
  Optional<CallerIdentity> resolve(HttpServletRequest request);
}
