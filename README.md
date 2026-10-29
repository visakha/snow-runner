# snow-runner

Java Spring Boot orchestrator for the Snowflake ETL — it calls the stored
procedures in sequence, with management control around the calls. It does
**not** replace any stored procedure and contains **no ETL logic**.

- Invoked by an external scheduler, via CLI and REST.
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

## Tooling and practices: SivaLabs + harness engineering

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

It also adopts the practices from SivaLabs' video
[Harness Engineering for Java and Spring Boot Applications](https://www.youtube.com/watch?v=H-Xtukxd1ao)
(37 minutes). The video's central idea, in one sentence: written
guidelines alone do not bind an AI coding agent — conventions must
also be encoded as automated checks that fail the build. Applied to
this project, when implementation starts:

- **Formatting:** Spotless with Palantir Java Format (already
  configured by a progen scaffold). Formatting is applied
  automatically and verified in the build — an unformatted change
  fails the build rather than waiting for review.
- **Coverage:** JaCoCo enforces a minimum coverage threshold that
  fails the build below it. The threshold itself is set at
  implementation time and raised over time; coverage is a floor,
  not a proof of correctness, and tests must cover real behavior
  rather than chase a percentage.
- **Module boundaries:** Spring Modulith boundary tests verify the
  module structure and fail on boundary violations and cycles.
  Applied here to this project's technical modules (the package
  areas in `001_SPEC.md` §12: api, cli, execution, catalog,
  persistence, domain) — not to business dimensions, which live in
  Snowflake, not in Java. The video's nested business-module layout
  is a preference, not a mandate: flat or nested is decided at
  implementation, and the tests then enforce whichever is chosen.
- **Architecture rules as tests:** ArchUnit, plus Taikai's
  ready-made rule sets, encode naming, visibility, and dependency
  direction as build-failing tests — including: repositories not
  public, controllers package-private and not depending on each
  other, services not depending on controllers, constructor
  injection only (no field injection), configuration classes named
  `*Config`, no generic `Exception` declarations, no deprecated
  APIs, test classes package-private and never `@Disabled`.
- **Agent skills as diagnostics only:** the Modulith-verifier style
  skill check is useful for finding violations, but it does not
  fail a build — the tests above are the enforcement. Skills guide;
  tests bind.
- **Deliberately not adopted:** Checkstyle. For an internal
  application like this one, Spotless formatting plus the rule
  tests above cover what matters; Javadoc/comment enforcement is
  for public libraries, not this project.

These practices constrain how implementation code is written and
verified. They do not change the spec's decisions (driver-level
steps, Java-owned run timestamps, YAML catalog, plain Spring Data
JDBC) — they are the harness that keeps future code, including
AI-written code, inside those decisions.
