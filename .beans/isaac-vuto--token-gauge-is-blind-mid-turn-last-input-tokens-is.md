---
# isaac-vuto
title: 'Token gauge is blind mid-turn: last-input-tokens is stamped once per turn, so a long tool-loop turn compacts off the undercounting estimate'
status: draft
type: bug
tags:
    - compaction
    - token-accounting
created_at: 2026-09-03T20:36:03Z
updated_at: 2026-09-03T20:36:03Z
---

Observed 2026-09-03 on zanebot (agent 0.1.41 / 13da406), session isaac-work-2 (scrapper, grok-4.6, window 500K, threshold 0.8): a fresh session ran ONE tool-loop turn from 17:49 to 18:47 (isaac-jarr work). Every mid-turn `:session/compaction-check` logged `:total-tokens ~304K` (61%) and never compacted; at turn end `:session/token-drift` fired and the provider stamp landed at **455,183** (91%). The session sat at 91% with 0 compactions.

Why (isaac-agent main, verified in source):
- `run-compaction-check!` (drive/turn.clj) computes `total-tokens` = `compaction/estimate-prompt-tokens` (a rebuild-and-count estimate) and hands it to `should-compact?`, whose `context-gauge` is `(max estimate (:last-input-tokens session-entry))` — correct design (pqjn/x2up).
- BUT `:last-input-tokens` is written only by `store-response!`, which `execute-llm-turn!` runs once for the FINAL assistant response of a turn ("Tool pairs are written mid-loop by record-tool-call!" — usage is not). Cycle 2..N provider responses carry `prompt_tokens` that never reach the store.
- So inside a turn the gauge's second leg is the PREVIOUS turn's stamp (0 for a fresh/archived session). The estimate undercounts grok prompts by ~33% (drift ratio ≈1.5 on this session; see isaac-1umd for the cycle-2+ body-size question), so a long turn sails past the threshold blind. The p9zy overflow compact-and-retry only fires when the provider actually rejects (500K wall), which is 100K past the threshold.

Expected: every provider response's `prompt_tokens` (each cycle, not just the last) updates `:last-input-tokens` before the next mid-turn compaction check, so the gauge tracks the real prompt within one cycle. Keep the estimate as the floor for the first cycle.

Runnable acceptance to write (@wip, features/session/compaction or token_accounting.feature): (1) a tool-loop turn whose cycle-2 provider usage reports prompt_tokens above threshold triggers mid-turn compaction on the next batch even though the estimate is below threshold; (2) the session entry's last-input-tokens after cycle 2 equals cycle 2's prompt_tokens (not the turn-final value); (3) existing pqjn stamp/drift scenarios stay green. Related: isaac-pqjn, isaac-p9zy, isaac-x2up, isaac-1umd, isaac-dgod (12M gauge on orchestration-verify — check whether the same per-turn stamping path is summing instead of replacing on a stateful Responses chain).
