package com.visakha.snowrunner.persistence;

import com.visakha.snowrunner.domain.Run;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * The one repository (002 §3.4). The Run aggregate owns its steps, but step rows are read and
 * written through the custom fragment, never through a mapped collection and never through a C_S_*
 * or V_* query — those belong to Compare, not to this application.
 *
 * <p>RUN rows are run bookkeeping, not ETL state: keeping all Java state in the control tables is
 * what makes runs stateless with respect to the ETL itself.
 */
@Repository
public interface RunRepository extends CrudRepository<Run, String>, RunRepositoryCustom {}
