package com.visakha.snowrunner.domain;

/**
 * Whether re-executing a step is safe. APPEND marks the AddressDoctor drivers: re-running them
 * re-appends DQM rows, so a resume must never re-execute a SUCCEEDED one.
 *
 * <p>DQM_ADDRESS is a pass output, not a work table: the constitution's run-start drop-and-truncate
 * bookends would not remove its rows, so skip-on-SUCCEEDED is the only protection against
 * double-append, and the crash window in the user guide stands.
 */
public enum RerunSafety {
  SAFE,
  APPEND
}
