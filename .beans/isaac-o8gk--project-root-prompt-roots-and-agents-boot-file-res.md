---
# isaac-o8gk
title: Project-root prompt roots and AGENTS boot-file resolution stay consistent
status: todo
type: bug
priority: normal
created_at: 2026-06-26T19:52:18Z
updated_at: 2026-06-26T20:55:15Z
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
