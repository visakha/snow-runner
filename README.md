# snow-runner

Java Spring Boot orchestrator for the Snowflake ETL — it calls the stored
procedures in sequence, with management control around the calls. It does
**not** replace any stored procedure and contains **no ETL logic**.

- Invoked by an external scheduler via REST; if health checks
  show the application is not alive, the scheduler launches it
  (java -jar) and then triggers via REST.
- Access is two-level: the scheduler controls (trigger, resume,
  cancel); production support reads observability and metrics via
  endpoints.
- Spring Boot + Spring Data JDBC (no JPA magic), typed models, explicit
  repositories, named SQL constants.
- Coding guidelines: [CODING_GUIDELINES.md](CODING_GUIDELINES.md) —
  necessary-only code, Google Java Style enforced in the Maven
  build, and the package arrangement kept in sync with the code.
- Spec-first: **no application code is written until the spec is
  approved.**

Start with [001_SPEC.md](001_SPEC.md) — it is v0.1 DRAFT, written for
review and discussion. Section 13 lists the open questions that need
answers before any implementation begins.

Document numbering: every markdown file generated for this project is
prefixed with a sequence number in order — `001_`, `002_`, and so on —
so the set and its order are visible from the filenames alone. Audio
files follow the same convention: `001_…`, `002_…`, and so on, in the
same sequence. Voice notes live in `audio/`, numbered to match their
document: `001_…` with the spec, `002_…` with the domain model,
`003_…` with the YAML consumption write-up, and so on.

## Tooling and practices: SivaLabs, kept simple

This project uses SivaLabs (Siva Prasad Reddy) tooling to bootstrap
and guide Spring Boot development:

- **sivalabs-agent-skills**
  (github.com/sivaprasadreddy/sivalabs-agent-skills) — Spring Boot
  skills for AI coding assistants (REST API development, Spring
  Modulith patterns, focused testing, MockMvcTester, ArchUnit,
  Docker Compose, Taskfile). These guide how code is written here;
  they do not enforce anything by themselves (see below).
- **progen CLI** (github.com/sivaprasadreddy/progen) — Go CLI that
  scaffolds a Spring Boot project (Maven/Gradle, migrations,
  security/JWT, Swagger, Testcontainers, Docker Compose, GitHub
  Actions, Spotless formatting, Taskfile). Note the adaptation for
  this project: progen's data-access default is Spring Data JPA,
  while snow-runner uses Spring Data JDBC — progen is used for
  scaffolding, and the data layer is adapted, not taken on JPA
  defaults.

Decision (2026-10-28): this is a simple project, so the video's
test harness is NOT adopted — no JaCoCo coverage enforcement, no
Spring Modulith, no ArchUnit/Taikai rule tests. That would be
over-engineering here. What is kept from SivaLabs is light: progen
scaffolding (with the Spring Data JDBC adaptation noted above) and
Spotless formatting, which ships with the scaffold and costs
nothing to keep. Agent skills remain guidance only.

What IS important here is observability, and that is specified in
004_OBSERVABILITY.md: CLI and REST as the touch points, plus
Azure-native logging, metrics, tracing, health, and alerting, so
there is a live view of the application during runtime.

## Code

The Java codebase now lives at the repo root (Maven single module,
Java 27, Spring Boot 4.1.2, Spring Data JDBC). The spec-first hold
above was overridden by direct order on 2026-10-30 for the Java
code only — code existing does not mean the open decisions are
settled. The 007 decisions round (control-table database name,
auth mechanism, dry-run role, idempotency-key format, target tree,
and the rest) remains open, and `BUILD_NOTES.md` records the
assumption made for each one.

```bash
mvn verify   # tests + Spotless + Checkstyle, all build-failing
```

What the code implements, and what it does not:

- It calls the 40 drivers in catalog order (YAML-driven, from
  `src/main/resources/steps-catalog.yml`), one driver CALL per
  step, sequential and fail-fast. It holds no ETL logic.
- Code comments cite the clean-ETL constitution
  (`docs/constitution/`) at every seam where it bites: stateless
  runs, boundaries Java never crosses (no `C_S_*` / `V_*` reads,
  no spine-predicate evaluation), and names that say source,
  dimension, and phase. No class or variable renames were needed
  in that pass — the tree already named source, dimension, and
  phase everywhere, which is the constitution's naming rule.
- The constitution's run-start bookends (drop work tables,
  truncate `C_S_*` tables) are NOT implemented in the app or in
  the drivers: both sides append today. Until the bookends land,
  resume — never re-trigger — is the recovery path. See the user
  guide's constitution section and the DevOps guide §13.
- Verified with mock data only (30/30 tests). No live Snowflake
  or Oracle run has been made from this codebase; the Oracle
  mirror is statically verified only and ships separately.

Operator docs: [docs/guides/USER_GUIDE.md](docs/guides/USER_GUIDE.md)
and [docs/guides/DEVOPS_GUIDE.md](docs/guides/DEVOPS_GUIDE.md).
Philosophy: [docs/constitution/clean-etl-constitution-guide.md](docs/constitution/clean-etl-constitution-guide.md)
and the deck in the same folder.
