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
so the set and its order are visible from the filenames alone.
