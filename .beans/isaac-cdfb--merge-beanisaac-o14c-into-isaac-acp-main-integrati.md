---
# isaac-cdfb
title: Merge bean/isaac-o14c into isaac-acp main (integration only)
status: completed
type: task
priority: high
created_at: 2026-07-10T11:53:05Z
updated_at: 2026-07-10T12:07:54Z
---

## Goal

Land isaac-o14c's VERIFIED work on isaac-acp main. Integration only — do NOT re-verify the bean.

## Situation

- origin/main is at 8e71510 (0.1.8 release: isaac-d84z attach replay).
- origin/bean/isaac-o14c (09cf9f3, verify-passed) branched from 78348b5 (pre-d84z) and carries 4 commits, including 368f20f 'restore d84z attach replay proof' — likely overlapping/duplicating d84z content that later landed on main.
- Merge, reconciling the replay code and features/comm/acp/session.feature + cli.feature so BOTH d84z's attach-replay behavior and o14c's head-only replay + method-scoped exact notification matching survive.
- Run isaac-acp bb spec + bb features under the repo's pinned sibling deps; fix only integration fallout. Push main.
- Do NOT bump the manifest version (the release train handles it).

## Worker note (2026-07-10, scrapper@isaac-work-1)

- Merged `origin/bean/isaac-o14c` into `isaac-acp` `main` (merge commit `56720da`; integration fix `e4d1476`).
- Resolved merge conflicts in `cli.feature`, `cli.clj`, `cli_spec.clj` (kept d84z attach-replay steps + o14c handler wiring).
- Integration fallout: o14c strict trailing `session/update` guard broke tool/compaction scenarios that allow extra message chunks; restored main’s sliding-window collection and scoped strict trailing guard to replay tables (`user_message_chunk` / `agent_message_chunk` only). `acp_steps_spec` trailing guard still green.
- Validation (`ISAAC_GIT=1` pinned siblings): `bb spec` 70/0 (1 pending); `bb features` 61/0 (5 pending). Pushed `main` to origin.
- No manifest version bump per bean.
