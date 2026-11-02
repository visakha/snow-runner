package com.visakha.snowrunner.persistence;

import com.visakha.snowrunner.domain.Run;
import java.util.List;

/** One page of runs plus the total, so the API can build the page envelope. */
public record RunPage(List<Run> content, long totalElements) {}
