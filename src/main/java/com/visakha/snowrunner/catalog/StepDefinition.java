package com.visakha.snowrunner.catalog;

import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.ProcedureName;
import com.visakha.snowrunner.domain.RerunSafety;
import com.visakha.snowrunner.domain.StepId;
import java.util.List;
import java.util.Set;

/**
 * One immutable catalog entry: one driver CALL. Position comes from list order (1-based): the
 * catalog file carries no explicit position field, so order in the file is the order of execution,
 * fixed once at load. Parameters stay an ordered list because CALL arguments are positional: a map
 * would lose the order the file declares.
 */
public record StepDefinition(
    StepId stepId,
    int position,
    ProcedureName procedureName,
    boolean enabled,
    List<Phase> phases,
    Set<Dimension> touches,
    RerunSafety rerunSafety,
    int timeoutMinutes,
    List<ParameterBinding> parameters) {}
