package com.visakha.snowrunner.domain;

/**
 * Filtering vocabulary for history reads only. Spine here is the constitution's spine — grain,
 * identity, dedup, the decisions that say what exists — and non-spine is the refinement work that
 * assumes the record exists. Java never evaluates a spine predicate; it only carries the
 * vocabulary.
 */
public enum SpineScope {
  SPINE,
  NON_SPINE,
  ALL
}
