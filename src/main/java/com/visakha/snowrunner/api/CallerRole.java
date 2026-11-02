package com.visakha.snowrunner.api;

/**
 * The two roles (006 §2). Scheduler controls and reads; support reads only. The mechanism proving a
 * role is still open (007) — this enum fixes which role each endpoint requires, not how the role is
 * proven.
 */
public enum CallerRole {
  SCHEDULER,
  SUPPORT
}
