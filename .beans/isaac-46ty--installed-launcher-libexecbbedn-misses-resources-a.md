---
# isaac-46ty
title: Installed launcher (libexec/bb.edn) misses resources/ and pins apron 3.0.0
status: completed
type: bug
priority: high
created_at: 2026-09-30T17:12:21Z
updated_at: 2026-09-30T17:19:48Z
---

Found 2026-09-30 while restoring foundation's http boot smoke. The installed CLI (brew keg: /opt/homebrew/bin/isaac -> <keg>/libexec/isaac-foundation/libexec/isaac) runs `bb --config libexec/bb.edn`. That file has `:paths ["../src"]` (no `../resources`) and its own `:deps` with c3kit apron **3.0.0**, while deps.edn/bb.edn use apron 3.2.1.

Effects: (1) since the handbook chapter moved to resources/ (df9c6d2) every launcher command warns `:module-index["isaac.foundation"].handbook - isaac/foundation/handbook.md not found` and handbook__read can't find foundation's chapter; confirmed absent at df9c6d2~1. (2) the installed CLI runs apron 3.0.0: no :default / :required / describe (isaac-dnib's schema defaults), so the deployed CLI would disagree with the server and the specs.

## Wanted
- libexec/bb.edn :paths include ../resources; its :deps match deps.edn's :deps (apron 3.2.1).
- A spec guards it: libexec/bb.edn :deps equal deps.edn :deps, and every deps.edn :paths entry appears (as ../<path>) in libexec/bb.edn :paths. Red first.
- `libexec/isaac --root <tmp root with {}> config validate` prints no handbook warning (one-time check, HOME isolated).

Ungated; planner verifies.

## Landed on main

- main-sha: isaac-foundation a7129dd

CI green: Slow features (@slow launcher lane), Server boot with a module-provided config type, verify (bb ci).

One-time check confirmed: on unpatched main, `libexec/isaac --root <tmp root with {}> config validate` printed
`warning: :module-index["isaac.foundation"].handbook - isaac/foundation/handbook.md not found`; after the fix it
prints only `OK - config is valid`.

`bb ci`'s `pins` task fails locally (before and after this fix, on both branches) with a `:model-exists?` lex
schema error — pre-existing environmental issue, unrelated to this bean. `bb spec` / `bb jvm-spec` with HOME
isolated show identical pre-existing failure counts on patched vs unpatched trees (the two new
`libexec_bb_edn_spec.clj` examples pass; no regressions).

## Planner verification (2026-09-30)

Verified: libexec/bb.edn now has ../resources and the same :deps as deps.edn; guard spec added; CI green on a7129dd (all 3 jobs).
