---
# isaac-gyk1
title: Split the overloaded 'the EDN isaac file contains:' gherclj step (write vs assert)
status: draft
type: task
created_at: 2026-06-18T16:25:55Z
updated_at: 2026-06-18T16:25:55Z
---

foundation spec step `the EDN isaac file "<path>" contains:`
(isaac-foundation spec/isaac/foundation/fs_steps.clj:360, body 291-325) is
DUAL-MODE on a hidden flag:

• :isaac-file-phase = :assert  -> slurps on-disk EDN, asserts the table rows.
• otherwise (default)          -> WRITES the table as EDN to the file.

Two problems:
1. One phrase ("...contains:") means both write AND assert; the disambiguator
   (:isaac-file-phase) is invisible in the scenario text. Reads like an
   assertion, defaults to a writer.
2. In isaac-foundation the flag is READ at fs_steps.clj:292 but NEVER SET
   anywhere (grep: no assoc/reset). The setter left with the monolith during
   extraction, so the :assert branch is DEAD CODE here — the step always
   writes.

## Proposed cleanup

Split into two clearly-named steps:
• `the isaac file "<path>" EDN contains:`  -> assert only (path/value table).
• keep a write step under an unambiguous "...exists with:" phrasing (we already
  have `the isaac EDN file <path> exists with:` at fs_steps.clj:369 — likely the
  intended writer; verify and consolidate onto it).
Then delete the dead :isaac-file-phase branch.

## Relationship

dhzy adds a NEW assert-only `the isaac file "<path>" EDN contains:` in cli_steps
(un-gated, CLI-context). This bean reconciles that with the fs_steps dual-mode
step so we don't end up with two overlapping EDN-file inspectors.
