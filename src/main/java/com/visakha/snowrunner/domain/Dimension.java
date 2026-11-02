package com.visakha.snowrunner.domain;

/**
 * Reporting slice a step touches. Dimension never drives execution in v0.1; it only describes what
 * a step covers. ADR is the catalog's spelling of the address dimension (002 says ADDR for the same
 * concept); the catalog file is authoritative here, so ADR is the enum value.
 */
public enum Dimension {
  PTY,
  RLE,
  ADR,
  PHN,
  EADDR,
  ALT_ID,
  PLN,
  REL,
  TOA,
  NM,
  MNG_ATTR
}
