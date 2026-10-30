# Coding guidelines

> Adopted 2026-10-30. These rules govern all code generated for
> this project, including AI-generated code. They are deliberately
> short: a rule that needs a paragraph to explain is a rule that
> will not be followed.
>
> This file is unnumbered on purpose, like README.md. The
> numbered sequence (001_, 002_, …) is for spec documents, and
> 007 stays reserved for the decisions round that is on hold.

## The one principle

Necessary only. No excessive tests, comments, documentation, or
code. If it does not serve the feature, the test, or the next
person reading the code, it does not get written. Prefer the
simpler code a mid-level engineer would write and maintain.

## Stack (decided, not negotiable without a new decision)

- Maven, single module (`pom.xml` + Maven wrapper).
- Java 27 (`maven.compiler.release=27`).
- Spring Boot 4.1.2 via the Boot BOM. All dependency versions
  come from the BOM; none is pinned individually.
- Spring Data JDBC only. No JPA anywhere: no JPA dependency,
  no JPA annotation, no `EntityManager`, no repository that
  behaves like JPA.
- No reflection. No invented mini-ORM, no string-built SQL, no
  runtime discovery of classes, methods, or columns.

## Java idioms to use

Enums, switch expressions, records, streams, and DTOs (as
records). Concretely: status and vocabulary are enums, never
strings compared by hand; multi-branch logic that picks a value
is a switch expression, not an if-else chain; DTOs and value
objects are records; collection processing uses streams.

Streams are sequential. There is no multi-threading in this
application: no `parallelStream`, no `ExecutorService`, no
background threads in the execution path. Execution is
sequential by design (see 001 §6), and the code stays that way.

## Tests

- One test method per API call: each controller operation gets
  exactly one focused test method. No test-per-branch explosion,
  no speculative edge-case suites.
- No Mockito. Test doubles are hand-written fakes and canned
  data, not framework mocks.
- Mock data IS allowed, explicitly: canned rows, fixture
  catalogs, and stub caller responses are fine in unit tests
  and in mock/demo runs (execution against fake data with no
  Snowflake behind it). "No mocks" means no Mockito, not no
  test data — mock data and mock runs are how behavior is
  rehearsed without a live database.
- Tests use real collaborators wherever a fake would hide the
  behavior under test. A test that passes only because its
  mocks agree with it proves nothing.

## Comments and documentation

- Comments explain why, never what. A comment that restates the
  code is deleted, not kept.
- No generated documentation beyond what the project already
  has: the numbered spec documents and this file. A new document
  needs a reason, not a template.

## Style and enforcement (Maven build)

- Google Java Style, enforced in the build, not by review:
  - Spotless with `googleJavaFormat` — `spotless:check` fails
    the build on unformatted code.
  - Checkstyle with `google_checks.xml` — fails the build on
    style violations.
- Both checks run on every build (`verify`). A change that
  fails either check is not a complete change.

## Package arrangement

The arrangement below is the approved structure (reviewed
2026-10-30, including the phase-package sketch). It is kept in
sync with the generated code by a simple rule: **any change
that adds, removes, renames, or moves a package or class
updates this listing in the same change.** A code change that
leaves this listing stale is incomplete, and a listing change
with no matching code change is not made.

Naming: `<Source><Dimension><Phase>` (e.g. `TrsPhoneLoader`,
`TtfPartyExc`); root dimension is Party; one class per
(source, phase, dimension), not per procedure. The loader
package is the spine stage; enrich, exc, and dqm together are
the non-spine stage. Phase classes delegate to stored
procedures — they do not write `C_S_*` tables themselves, and
preflight stays read-only (see the phase sketch notes).

```
src/main/java/com/visakha/snowrunner/
├── SnowRunnerApplication.java
├── api/
│   ├── RunController.java
│   ├── WorkflowController.java
│   ├── dto/ (RunResponse, StepResponse, TriggerRunRequest, ErrorResponse)
│   └── ApiExceptionHandler.java
├── catalog/
│   ├── StepCatalog.java
│   ├── StepDefinition.java
│   ├── YamlStepCatalog.java
│   └── CatalogException.java
├── domain/
│   ├── Run.java
│   ├── RunStep.java
│   ├── Clock.java
│   ├── RunId.java, RunTimestamp.java, ProcedureName.java, StepId.java
│   └── RunStatus, StepStatus, Source, Domain, Dimension, SpineScope, RerunSafety
├── execution/
│   ├── RunExecutor.java
│   ├── ResumePolicy.java
│   ├── PreflightService.java
│   ├── PreflightCheck.java
│   ├── RunQueryService.java
│   └── ProcedureCaller.java
├── persistence/
│   ├── RunRepository.java
│   └── RunQueries.java
├── infra/
│   ├── snowflake/ (SnowflakeProcedureCaller)
│   ├── time/ (SystemClock)
│   └── config/ (SnowflakeProperties, CatalogProperties)
├── trs/   (no valexc: TRS has no Validation driver)
│   ├── preflight/ (TrsPreflight)
│   ├── loader/ (root: TrsPartyLoader; dimension: TrsRelationshipLoader,
│   │   TrsToaLoader, TrsPhoneLoader, TrsEmailLoader, TrsAltIdLoader,
│   │   TrsPlanLoader, TrsAddressLoader, TrsNameLoader, TrsAttrLoader)
│   ├── enrich/ (root: TrsPartyEnrich; dimension: TrsPhoneEnrich)
│   ├── exc/ (root: TrsPartyExc; dimension: TrsPhoneExc, TrsEmailExc,
│   │   TrsAddressExc, TrsAltIdExc, TrsPlanExc, TrsRelationshipExc, TrsToaExc)
│   ├── dqm/ (TrsAddressDqm)
│   ├── flush/ (TrsFlush)
│   └── postflight/ (TrsPostflight)
├── eci/
│   ├── preflight/ (EciPreflight)
│   ├── valexc/ (EciValExc)
│   ├── loader/ (root: EciPartyLoader; dimension: EciAddressLoader,
│   │   EciPhoneLoader, EciEmailLoader, EciAltIdLoader,
│   │   EciRelationshipLoader, EciReactivationLoader)
│   ├── enrich/ (root: EciPartyEnrich; dimension: EciPhoneEnrich)
│   ├── exc/ (root: EciPartyExc; dimension: EciPhoneExc, EciEmailExc,
│   │   EciAddressExc, EciAltIdExc, EciRelationshipExc)
│   ├── dqm/ (EciAddressDqm)
│   ├── flush/ (EciFlush)
│   └── postflight/ (EciPostflight)
├── fss/
│   ├── preflight/ (FssPreflight)
│   ├── valexc/ (FssValExc)
│   ├── loader/ (root: FssPartyLoader; dimension: FssAddressLoader,
│   │   FssPhoneLoader, FssEmailLoader, FssAltIdLoader,
│   │   FssRelationshipLoader)
│   ├── enrich/ (root: FssPartyEnrich; dimension: FssPhoneEnrich)
│   ├── exc/ (root: FssPartyExc; dimension: FssPhoneExc, FssEmailExc,
│   │   FssAddressExc, FssRelationshipExc)
│   ├── dqm/ (FssAddressDqm)
│   ├── flush/ (FssFlush)
│   └── postflight/ (FssPostflight)
├── wic/
│   ├── preflight/ (WicPreflight)
│   ├── valexc/ (WicValExc)
│   ├── loader/ (root: WicPartyLoader; dimension: WicAddressLoader,
│   │   WicPhoneLoader, WicEmailLoader, WicAltIdLoader)
│   ├── enrich/ (root: WicPartyEnrich; dimension: WicPhoneEnrich)
│   ├── exc/ (root: WicPartyExc; dimension: WicPhoneExc, WicEmailExc,
│   │   WicAddressExc)
│   ├── dqm/ (WicAddressDqm)
│   ├── flush/ (WicFlush)
│   └── postflight/ (WicPostflight)
├── ttf/   (no dqm; no Address/Email — TTF's runner has no such driver)
│   ├── preflight/ (TtfPreflight)
│   ├── valexc/ (TtfValExc)
│   ├── loader/ (root: TtfPartyLoader; dimension: TtfPhoneLoader, TtfAltIdLoader)
│   ├── enrich/ (root: TtfPartyEnrich; dimension: TtfPhoneEnrich)
│   ├── exc/ (root: TtfPartyExc; dimension: TtfPhoneExc)
│   ├── flush/ (TtfFlush)
│   └── postflight/ (TtfPostflight)
└── cre/   (no valexc, no dqm)
    ├── preflight/ (CrePreflight)
    ├── loader/ (root: CrePartyLoader; dimension: CreAddressLoader,
    │   CrePhoneLoader, CreAltIdLoader)
    ├── enrich/ (dimension: CrePhoneEnrich)
    ├── exc/ (root: CrePartyExc; dimension: CrePhoneExc, CreAddressExc,
    │   CreAltIdExc)
    ├── flush/ (CreFlush)
    └── postflight/ (CrePostflight)
src/main/resources/
├── application.yml
├── steps-catalog.yml
└── db/migration/
    └── V1__create_control_tables.sql
src/test/java/com/visakha/snowrunner/   (mirrors main, package for package)
src/test/resources/                      (test catalog YAML, application-test.yml)
```
