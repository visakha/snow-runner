package com.visakha.snowrunner.catalog;

/**
 * Flow phases a driver executes internally. Closed: the YAML spells them lowercase, and any other
 * spelling fails at load instead of becoming a silent typo.
 *
 * <p>Constitution mapping: LOADER is the spine stage (it lands rows on C_S_*); ENRICH, EXC, and DQM
 * are the non-spine stage (they refine off landed tables); VALEXC is the validation gate that runs
 * first in the Validation drivers, maintaining the inactive list the spine filters on.
 */
public enum Phase {
  LOADER("loader"),
  ENRICH("enrich"),
  EXC("exc"),
  DQM("dqm"),
  VALEXC("valexc");

  private final String yamlName;

  Phase(String yamlName) {
    this.yamlName = yamlName;
  }

  /** Returns the YAML spelling. */
  public String yamlName() {
    return yamlName;
  }

  /** Parses a YAML phase value, failing with location on anything unknown. */
  public static Phase fromYaml(String value, String location) {
    for (Phase phase : values()) {
      if (phase.yamlName.equals(value)) {
        return phase;
      }
    }
    throw new CatalogException(location + ": unknown phase '" + value + "'");
  }
}
