package com.visakha.snowrunner.domain;

/** The six ETL sources. Closed: a new source is a code change, not a config edit. */
public enum Source {
  TRS,
  ECI,
  FSS,
  WIC,
  TTF,
  CRE
}
