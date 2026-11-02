# A constitution for clean ETL

**What this is.** Principles, ground rules, and shared language for the
team. Not a technical specification — a way of thinking before a way of
coding. Companion to the presentation of the same name (18 slides, light
theme). No spine is drawn anywhere in the deck, by request; the ideas
below are carried in words.

## Why a constitution?

Code rots when thinking is fuzzy. A pipeline nobody can explain is a
pipeline nobody can safely change.

A constitution settles the recurring arguments once: what state a run
may keep, what crosses a boundary, what a name must tell you.

The product goal: a pipeline so self-evident that the next developer
reads it top to bottom and never has to ask what it does. The code
follows the constitution — not the other way round.

## Domain knowledge first

The domain: members (people) and their roles — addresses, phones,
emails, identifiers, relationships, plans, assistance.

A developer who knows the domain writes less code. Most "complex" logic
is just the domain stated carefully.

Know the grain before you write a line: one row per what? If you cannot
answer that, you are not ready to build.

Domain facts that shape everything here: staging arrives as deltas,
history is Type 2 where it exists, downstream MDM consumes through
views.

## Principle 1 — Stateless pipelines

A run depends only on its inputs: the day's staging deltas plus
reference data. Nothing else.

No memory between runs. Yesterday's work tables, yesterday's flags,
yesterday's leftovers are not inputs.

Rerun gives the same result. Run it twice, get the same landed data. If
it doesn't, something is holding hidden state.

If a step truly needs yesterday's state, that state becomes an explicit,
named input — a history table or a reference table — never an accidental
leftover.

## Principle 2 — See the boundaries

Four boundaries, four contracts: raw sources → staging (delta) → spine
landing (CS tables) → refinement (passes and views) → downstream MDM.

A boundary is a contract: it says exactly what crosses, in what shape,
and who owns each side.

A workflow stitches across boundaries by calling the next stage, never
by reaching around it: loaders land, passes refine, views present.

If you cannot draw the boundary a step sits on, the step is in the
wrong place — or the boundary is missing.

## Principle 3 — Temporary tables: a smell, not a sin

A temporary working table is a claim: "this step cannot finish without
holding state across a boundary." Treat every such claim as suspicious.

Reduce, don't romanticise: most cross-step state dissolves when the
grain is right and the step order is right.

When a work table is essential, it must earn its place: listed, named
for its reason, and given a lifetime.

Rule: no work table survives the run that made it.

## The daily lifecycle — the rule

1. **Run starts.** A new run begins.
2. **Drop + truncate.** Work tables are dropped; CS tables are
   truncated.
3. **Fresh load.** Loaded fresh from staging deltas.
4. **The day's truth stands.** CS tables stand for interpretation;
   views feed MDM.
5. **Next day: repeat.** Same rule, every day, every workflow.

CS tables are the interpretive layer: the team reads the day's truth
there, through the lenses, before it flows downstream. They exist
because views sit on top of them, and the views feed the downstream MDM
systems.

Work tables are scaffolding: they hold the run up while it works, then
come down.

Downstream reads only after the load: views present a complete day,
never a half-loaded one.

## Where delta detection lives

Delta detection is NOT the ETL's job. A separate staging workflow pulls
the raw source systems and writes stage tables — and it writes deltas.
That codebase is separate from this ETL and is not covered by this
constitution beyond this boundary: it owns "what changed."

The ETL consumes deltas. It never re-detects change, never diffs
yesterday against today, never guesses what moved.

Why: one owner for "what changed," one place to fix when change
detection is wrong, and an ETL that stays stateless.

Consequence: if staging ever ships a full snapshot where a delta was
promised, that is a staging bug — not an ETL enhancement request.

## Delta tables (_D_) — the norm

_D_ tables are delta tables: only what changed since the last pull.

The norm: most ETLs in this estate read _D_ tables only. TRS examples:
`D_DC_INDV`, `D_DC_ALIAS`, `D_DC_CASES`, `D_DC_RELATIONSHIPS`,
`D_CV_DC_MCI_INDV`, `D_CV_MCI_EDG_INDV_TOA`, `D_UNQ_ADDR`, `D_LU_TOA`.

Delta-first is what makes stateless pipelines possible: the run's input
is today's change, not the world's history.

A mapping that reads a _D_ table should never also need yesterday's
output to make sense of it.

## History tables (_H_) — Type 2, kept, never touched

_H_ tables are history tables: Type 2 slowly-changing-dimension
history — the same business key, kept across time, with effective
dates.

The rule: if the logic requires history, keep it. We do not touch,
rewrite, or "clean up" history tables. They are the record.

The ETL only ever READS _H_ tables — it never writes them. History is
an explicit input, not leftover state, and that is why it is allowed to
cross runs.

### Mappings that use history tables

| Mapping (loader) | History table read | Why history is needed |
|---|---|---|
| ECI reactivation — family address (`sp_sf_load_ECI_RFA`) | `STG_ECI_H_FAMILY_ADDRESS` | History is how a reactivated family address is rebuilt (latest load per case). |
| ECI reactivation — family phone (`sp_sf_load_ECI_RFP`) | `STG_ECI_H_FAMILY_PHONE` | Same pattern, for reactivated family phones. |
| ECI reactivation — Medicaid (`sp_sf_load_ECI_RFM`) | `STG_ECI_H_MEDICAID` | Same pattern, for reactivated Medicaid rows. |
| TRS alternate IDs (`sp_sf_load_TRS_ALT`) | `STG_TRS_H_DC_INDV` | Validated-SSN reference: SSA-validated, positive SSNs, joined to the incoming alternate IDs. |
| IDQ relationships deactivation history (IDQ only) | `STG_TRS_H_DC_RELATIONSHIPS_A` | Used by the IDQ deactivation-history flow. This read is not carried into the v3 loaders. |

## Work tables per workflow

Every work table below is written by the ETL during a run and read back
by a later step. Under the daily lifecycle, all of them are dropped at
the next run's start.

### TRS and CRE

| Workflow | Work table | Why it exists |
|---|---|---|
| TRS Ongoing | `STG_TRS_VALID_MEMBERS` | The valid-member list: every downstream dimension keeps only members on it. |
| TRS Ongoing | `STG_TRS_D_UNQ_ADDR` | The deduped unique-address set: ADDR1 builds it, ADDR2 lands from it and writes DQM. |
| TRS Deactivation | `STG_TRS_DEACTIVED_MEMBERS` | The deactivated-member list: PTY reads it, deactivation maintains it. |
| TRS Deactivation | `STG_TRS_DEACTIVATED_RELATIONSHIPS` | The deactivated-relationship list, used inside relationship deactivation. |
| TRS TOA | (reads `VALID_MEMBERS`; writes none) | TOA needs the member list but creates no state of its own. |
| CRE | (none) | CRE holds everything inside its loader; no cross-step state escapes the procedure. |

### ECI, FSS, TTF, WIC, and shared

| Workflow | Work table | Why it exists |
|---|---|---|
| ECI Reactivation | `STG_ECI_D_MEDICAID`, `STG_ECI_D_FAMILY_PHONE`, `STG_ECI_D_FAMILY_ADDRESS` | Family-level staging for reactivated families, so the reactivation publish can join them back. |
| ECI / FSS / TTF / WIC Validation | `*_PTY_INACTIVE_STS` | The live inactive-member list the ongoing loaders filter on (maintained by valexc). |
| ECI / FSS / TTF / WIC Validation | `*_PTY_INACTIVE_LOG` | Audit log of inactive members — written, never read in-tree. |
| Shared, all workflows | `EXC_STG_BAD_REC_MEMB` | The exception queue: one row per failed check. |
| Shared, 4 address drivers | `DQM_ADDRESS` | AddressDoctor output per landed address. |

Note on the audit logs: the `*_PTY_INACTIVE_LOG` tables are written
but never read by any step in the tree. Under the drop rule they go
with the other work tables — unless the team explicitly exempts them as
audit history (see "Adopting this constitution").

## Terminology — spine, load-bearing, non-spine

**Spine:** the load-bearing decisions — grain, identity, dedup. The
spine decides what exists.

**Load-bearing:** if it breaks, everything downstream breaks with it.
That is the test — not decoration.

**Non-spine:** work that assumes the record exists and refines, checks,
or explains it — cleansing, standardisation, enrichment, lookups,
exception detection, audits, diagnostics.

Non-spine is not unimportant. A future date of birth flagged as bad data
matters. It just does not decide existence — and that distinction keeps
the pipeline honest about what depends on what.

## Terminology — dimensions and lenses

**Dimension:** a slice of a domain. Member is the domain; party,
address, phone, email, identifier, relationship, plan, assistance are
dimensions.

A dimension is the unit a developer reasons about: one loader, one
landing table, one set of checks per dimension.

**Lens:** a way of seeing the same landed data. Three lenses sit on
every CS table: `V_MDM_*` (business rows only — what flows to MDM),
`V_CMP_*` (business rows + flags — what compare judges), `V_AUDIT_*`
(flagged rows only — what needs attention).

Lenses are why the tables can stay complete: nothing is deleted for a
consumer; each consumer looks through its own lens.

Three questions before you write a step: does it decide existence
(spine)? Which dimension does it belong to? Which lens will read its
output?

## Naming conventions — the first documentation

A name is the first documentation. If you must open the code to learn
what a thing is, its name failed.

- `C_S_*` = landed spine tables (source + dimension).
- `STG_*` = staging and work tables.
- `V_MDM_*` / `V_CMP_*` / `V_AUDIT_*` = lenses.
- `sp_sf_load_*` = loader. `sp_common_pass_*` = shared pass.
  `sp_common_enrich_*` = shared enrichment.
- `_D_` = delta. `_H_` = history. The segment tells you the table's
  time-shape before you read a row.
- A procedure name says source, dimension, and phase:
  `sp_sf_load_TRS_ADDR2`, `sp_common_pass_adr_dqm`. No clever
  abbreviations, no reused names for different things.

## Self-evident and self-sustaining

**Fluent staging:** each step's output is the next step's obvious
input. The pipeline reads like a sentence: land, refine, present.

**No hidden state:** no step depends on a leftover, a side table nobody
listed, or a flag set somewhere unrecorded.

**No tribal knowledge:** a new developer follows the names, the
boundaries, and the listed work tables — and understands the run without
asking anyone.

**Self-sustaining:** the same shape for every source, so the tenth
workflow costs as much thought as the second. Thin wrappers, one true
implementation.

## The constitution — ten ground rules

1. A run is stateless: inputs are staging deltas + reference data.
   Nothing else.
2. Delta detection lives in the staging workflow, never in the ETL.
3. Every boundary is a contract: name what crosses it, in what shape.
4. Work tables are guilty until proven essential — and then listed,
   justified, and given a lifetime.
5. Every run starts by dropping work tables and truncating CS tables;
   every run ends with CS tables holding the day's truth.
6. History (_H_) is read-only and untouchable; delta (_D_) is the norm.
7. Nothing is deleted for a consumer: consumers look through lenses
   (views), not through deletions.
8. Names tell source, dimension, and phase. If a name needs a comment to
   explain it, rename it.
9. One true implementation per dimension, thin wrappers per source —
   never five copies of the same logic drifting apart.
10. If a step's boundary, dimension, or lens is unclear, stop and
    clarify before coding.

## Adopting this constitution — honestly

The constitution states the rule. One gap to close in code: v3 loaders
today append (insert-only). The run-start drop + truncate bookends are
not yet in the loaders — conformance means adding those bookends to the
runners, not rewriting the loaders.

One decision to record: `*_PTY_INACTIVE_LOG` tables — drop with the
other work tables, or explicitly exempt as audit history? A constitution
with an unnamed exception is just a suggestion. Name the exemption.

The next workflow built to these rules is the proof: listed work
tables, named boundaries, a stateless rerun — and a new developer who
can read it cold.

Closing line: land the core, refine off the tables, leave nothing
hidden. If the next person cannot follow it without asking, it is not
finished.
