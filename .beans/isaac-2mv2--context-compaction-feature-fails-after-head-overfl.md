---
# isaac-2mv2
title: "Context compaction feature fails after head overflow scenario"
status: todo
type: bug
priority: normal
created_at: 2026-05-12T00:15:29Z
updated_at: 2026-05-12T00:15:29Z
---

## Description

Why
Full bb features remains red outside isaac-yonq scope.

Observed behavior
Feature failure: Context Compaction Logging / compaction succeeds and chat continues when the head exceeds the context window.

Reproduction
Run bb features and observe the failing scenario in features/context/compaction.feature.

Notes
bb spec is green. Focused module/manifest slices for isaac-yonq are green. The current worktree also contains unrelated preexisting session/compaction edits.

