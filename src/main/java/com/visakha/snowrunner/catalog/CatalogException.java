package com.visakha.snowrunner.catalog;

/**
 * A catalog problem. Always a startup failure, never a runtime surprise: every message names the
 * file, the workflow, and the step so the operator can fix the file, not guess.
 */
public class CatalogException extends RuntimeException {
  /** Creates the exception with a located message. */
  public CatalogException(String message) {
    super(message);
  }

  /** Creates the exception wrapping a parse failure. */
  public CatalogException(String message, Throwable cause) {
    super(message, cause);
  }
}
