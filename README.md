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
