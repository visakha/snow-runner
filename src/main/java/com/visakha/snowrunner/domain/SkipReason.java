package com.visakha.snowrunner.domain;

/** Closed skip vocabulary (005 §5). No other reason string is invented at runtime. */
public enum SkipReason {
  DISABLED,
  FAIL_FAST,
  CANCELLED,
  APP_STOPPED
}
