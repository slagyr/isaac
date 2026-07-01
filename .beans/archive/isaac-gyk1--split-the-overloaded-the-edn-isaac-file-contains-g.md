---
# isaac-gyk1
title: Split the overloaded 'the EDN isaac file contains:' gherclj step (write vs assert)
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-18T16:25:55Z
updated_at: 2026-06-18T18:28:00Z
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

## Implementation (work-3)

- Removed dual-mode `edn-isaac-file-contains` and `:isaac-file-phase` from
  `fs_steps.clj` (spec + spec-support mirror).
- Assert: `Then the isaac file "<path>" EDN contains:` → `isaac-file-edn-contains`.
- Write: `Given the isaac EDN file <path> exists with:` → `isaac-edn-file-exists`
  (removed duplicate `the EDN isaac file … contains/exists` phrasing).
- Removed duplicate assert from `cli_steps.clj` (single assert lives in fs_steps).
- Migrated consumer `.feature` files and dropped `:isaac-file-phase :assert` from
  cron/hail/imessage step namespaces (imessage: removed from
  `imessage-delivery-worker-ticks` in isaac-imessage `a597a56`).
- Foundation v0.1.0 at `36e4a6f` (includes hail session-id parse alignment with
  `state-id-value`). Agent `dc93739`, server `d3ffd7f` pins bumped in hail/cron/imessage.
- CI green: foundation, agent, hail (spec+features), cron, imessage.

## Verification notes

- Verified on 2026-06-18 on current heads. Foundation has the split in place: assert-only [fs_steps.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/foundation/fs_steps.clj:337) and the unambiguous writer at [fs_steps.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/foundation/fs_steps.clj:342).
- Repo sweep is clean: `rg -n "isaac-file-phase" isaac-foundation isaac-agent isaac-server isaac-cron isaac-hail isaac-imessage` returned no matches.
- The stale imessage issues are fixed on current `main`: [imessage_steps.clj](/Users/micahmartin/agents/verify/isaac-imessage/spec/isaac/comm/imessage/imessage_steps.clj:52) no longer sets `:isaac-file-phase`, and [deps.edn](/Users/micahmartin/agents/verify/isaac-imessage/deps.edn:40) now pins `marigold.bridge` and `marigold.longwave` to foundation SHA `36e4a6f10a02b86008eb81aaa20b057387bb4c7a`.
- Verification runs were green: `env ISAAC_GIT=1 bb features-all features/module/modules.feature features/module/modules_list.feature features/cli/init.feature` in `isaac-foundation` → `11 examples, 0 failures, 34 assertions`; `env ISAAC_GIT=1 bb spec spec/isaac/server/imessage_app_spec.clj` in `isaac-imessage` → `34 examples, 0 failures, 56 assertions`; `env ISAAC_GIT=1 bb features` in `isaac-imessage` → `15 examples, 0 failures, 20 assertions, 3 pre-existing pending`.

## Re-handoff (work-3)

Verifier failure was from a stale `verify/` checkout. `origin/main` on
isaac-imessage has had the `(g/assoc! :isaac-file-phase :assert)` line removed
since `a597a56`; `grep isaac-file-phase` is clean across work-3. Synced
`plan/` and `verify/` imessage mirrors — `imessage_steps.clj:52` is now
`imessage-delivery-worker-ticks` with no phase flag. `bb ci` green on work-3
isaac-imessage at `7b2c4de`.
