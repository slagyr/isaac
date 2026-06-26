---
# isaac-o8gk
title: Project-root prompt roots and AGENTS boot-file resolution stay consistent
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-26T19:52:18Z
updated_at: 2026-06-26T21:11:00Z
---

Current behavior is inconsistent: prompt discovery uses a discovered project root, but boot-file loading reads only <cwd>/AGENTS.md, and project-local prompts currently default to <project-root>/prompts. We want one contract: discover the nearest project root by walking up from session cwd, then load boot instructions from <project-root>/AGENTS.md and project-local prompt content from <project-root>/.isaac/prompts. No ancestor prompt merging; no cwd-only prompt resolution.\n\nAcceptance ideas:\n- project-local prompt discovery reads from <project-root>/.isaac/prompts\n- a session started in a nested subdirectory still discovers the nearest ancestor project root\n- AGENTS.md is read from that discovered project root, not just the literal cwd\n- prompts remain categorized by type: > user-invocable: > directory inference (commands/skills/rules)\n- there is no merging of prompt roots from multiple ancestors\n

## Verification

Verified on fetched GitHub `isaac-agent` `main` at `91ea8ef980930289d9d22a69007879bd30be86f5`.

- [src/isaac/prompt/catalog.clj](/private/tmp/isaac-o8gk-agent/src/isaac/prompt/catalog.clj:200) now discovers the nearest project root by walking ancestors for `AGENTS.md` / `.isaac/prompts` markers.
- Project typed prompts now resolve from `<project-root>/.isaac/prompts`, not `<project-root>/prompts`.
- [src/isaac/session/context.clj](/private/tmp/isaac-o8gk-agent/src/isaac/session/context.clj:23) reads `AGENTS.md` from the discovered project root rather than literal cwd.

Proofs were green:

- `bb spec spec/isaac/prompt/catalog_spec.clj spec/isaac/session/context_spec.clj` -> `33 examples, 0 failures`
- `bb features features/prompts/catalog.feature features/prompts/rules.feature features/session/boot.feature` -> `14 examples, 0 failures`

## Reopened

Reopened at user request because the planning pass did not land before the bean
was worked and verified. The feature scenarios now exist on `isaac-agent` main
and are the intended contract for this bean:

- `isaac-agent/features/prompts/catalog.feature:91` — project `.isaac/prompts`
  command shadows global
- `isaac-agent/features/prompts/catalog.feature:116` — nearest project root
  discovered from nested cwd
- `isaac-agent/features/session/boot.feature:9` — `AGENTS.md` loaded from
  project root
- `isaac-agent/features/session/boot.feature:25` — `AGENTS.md` discovered from
  nested cwd

Reopening so bean state reflects the missing upfront planning work rather than
leaving it closed as if the feature spec had been established first.



## Worked 2026-06-26 — verify & hand off (no code change)

Per user direction ("verify & hand off only"), treated the four landed
scenarios as the complete intended contract; made no code or scenario changes.

State: implementation AND the contract scenarios are already on isaac-agent
main @ 75f4ef5 ("isaac-95lv: drop duplicated comm factory/registry"), which is
newer than the 91ea8ef cited in the prior Verification section. Re-verified the
whole contract holds on current main:

Contract scenarios (all green):
- features/prompts/catalog.feature:91  — project .isaac/prompts command shadows global
- features/prompts/catalog.feature:116 — nearest project root discovered from nested cwd
- features/session/boot.feature:9      — AGENTS.md loaded from project root
- features/session/boot.feature:25     — AGENTS.md discovered from nested cwd

Implementation backing the contract:
- src/isaac/prompt/catalog.clj: discover-project-root walks ancestors for
  AGENTS.md / .isaac/prompts markers, returns the SINGLE nearest one;
  project-roots builds only from that root (no ancestor merging). Project
  specs concatenated after global -> project shadows global on the index reduce.
  Project typed prompts resolve from <project-root>/.isaac/prompts.
- src/isaac/session/context.clj:23 read-boot-files reads AGENTS.md from the
  same discovered project root (not literal cwd).

Proofs (CI-faithful; :spec/:features use pinned foundation deps, target/gherclj
cleaned between runs):
- clojure -M:spec spec/isaac/prompt/catalog_spec.clj spec/isaac/session/context_spec.clj -> 33/0
- clojure -M:features (the 3 contract features) -> 14/0
- FULL clojure -M:spec     -> 1110/0
- FULL clojure -M:features -> 550/0

Known contract gap (intentionally NOT addressed, per scope decision): the
acceptance idea "no merging of prompt roots from multiple ancestors" is
enforced by the implementation (single nearest root) but has no guarding
feature scenario. Left for a future planning pass if desired.

Tagged unverified for verifier confirmation on a fresh checkout of
isaac-agent main @ 75f4ef5.
