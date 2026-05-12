---
# isaac-7xtd
title: "Move isaac.prompt → isaac.llm.prompt"
status: completed
type: task
priority: low
created_at: 2026-05-07T19:29:23Z
updated_at: 2026-05-08T04:02:23Z
---

## Description

isaac.prompt.* (builder + anthropic) exists exclusively to serve the Api adapters in isaac.llm.api.*. The current top-level placement implies a project-wide concern, but the only production consumers are isaac.drive.turn (dispatching to an Api), isaac.llm.api.grover (estimate-tokens), isaac.context.manager (now soon isaac.session.compaction — wire-shape helpers), and the two prompt files themselves.

Moving under isaac.llm.prompt.* names the consumer relationship: these are utilities the Api adapters use. The dependency direction reads correctly afterwards (isaac.llm.api.<name> → isaac.llm.prompt.*, never the other way).

Changes:

1. File + ns moves:
   - src/isaac/prompt/builder.clj      → src/isaac/llm/prompt/builder.clj
   - src/isaac/prompt/anthropic.clj    → src/isaac/llm/prompt/anthropic.clj
   - spec/isaac/prompt/builder_spec.clj   → spec/isaac/llm/prompt/builder_spec.clj
   - spec/isaac/prompt/anthropic_spec.clj → spec/isaac/llm/prompt/anthropic_spec.clj

2. Update production callers (4 files):
   - src/isaac/drive/turn.clj                       (requires both)
   - src/isaac/llm/api/grover.clj                   (requires builder)
   - src/isaac/context/manager.clj                  (requires builder; or src/isaac/session/compaction.clj if isaac-yfb4 has landed)
   - src/isaac/prompt/anthropic.clj                 (internal cross-reference to builder; updates as part of the move)

3. Update spec callers (3 files):
   - spec/isaac/context/manager_spec.clj            (or session/compaction_spec.clj post-yfb4)
   - spec/isaac/features/steps/session.clj
   - spec/isaac/features/steps/providers.clj

4. Remove src/isaac/prompt/ and spec/isaac/prompt/ directories.

No behavior change. Only naming and placement. Mechanical refactor.

Out of scope (deferred to follow-up bead):
- Lifting estimate-tokens / truncate-tool-result out of prompt/ to better-fitting homes.
- Pushing prompt-building behind the Api protocol (per-adapter build-prompt). That's the architectural follow-up.

## Acceptance Criteria

bb spec green; bb features green; grep -rn 'isaac\.prompt\.' src spec returns nothing (only isaac.llm.prompt.*); src/isaac/prompt/ and spec/isaac/prompt/ removed; isaac.llm.api.grover, isaac.drive.turn, isaac.session.compaction (or isaac.context.manager pre-yfb4) all require isaac.llm.prompt.*.

## Design

Considered keeping isaac.prompt as-is, splitting prompt vs prompt-utility, and dissolving prompt entirely into adapters. Picked the rename because: (1) the architectural restructure (per-adapter build-prompt) is a separate, larger change worth its own bead; (2) the rename alone removes the misleading top-level placement and is mechanical/low-risk; (3) it gives the architectural follow-up a clean target namespace to evolve within.

## Notes

Verification failed: bb spec did not complete cleanly in the verification run and bb features fails with 13 regressions. The regressions appear related to this bead, not unrelated baseline failures. This change introduced an out-of-scope architectural shift by adding build-prompt to isaac.llm.api and routing isaac.drive.turn/build-chat-request through it, even though the bead explicitly deferred pushing prompt-building behind the Api protocol. In src/isaac/drive/turn.clj, build-turn-ctx computes turn-ctx via session-ctx/resolve-turn-context but then returns the original opts-backed model, provider, soul, and context-window instead of the resolved values. That drops config-derived turn context for ACP, cron, memory channel, and similar paths, matching the observed feature failures where expected messages or compactions are nil or cron runs fail.

