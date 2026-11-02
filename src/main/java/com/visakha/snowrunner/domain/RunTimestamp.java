package com.visakha.snowrunner.domain;

import java.time.Instant;

/**
 * The run's trigger timestamp, recorded on RUN and carried on every StepCall. Today the catalog
 * binds no parameters, so drivers receive zero arguments and generate their own row stamp
 * internally: RUN.RUN_TS is trigger time, not a row stamp, and must not be read as one. It is
 * reused on resume so a resumed attempt is recognizably the same run, and the $RUN_TIMESTAMP
 * binding exists for a catalog that binds it in future.
 */
public record RunTimestamp(Instant value) {}
