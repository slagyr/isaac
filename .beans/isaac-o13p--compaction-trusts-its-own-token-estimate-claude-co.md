---
# isaac-o13p
title: Compaction trusts its own token estimate; claude-code context reached ~1M tokens
status: in-progress
type: bug
priority: high
created_at: 2026-10-02T04:41:34Z
updated_at: 2026-10-02T14:35:41Z
---

Found 2026-10-02 on yopp, ACP session acp-2026-09-29-1639-540a (model claude-sonnet → claude-sonnet-5 via :claude-code, :context-window 1000000, :context-mode :full, rubberband, threshold 0.8). Every cycle `:turn/request-measured` showed estimated ~311K vs provider-reported ~500K (ratio ~1.61), while `:session/compaction-check` sat at ~502K (under the 800K trigger). The 04:24:08Z request reported prompt-tokens 1,004,210 (cache-write 929,288 — a cache miss). Likely also why the Claude subscription limit was hit.

isaac-agent compaction.clj `should-compact?` (116-118) only sees `compaction/context-gauge` → `prompt-builder/estimate-tokens`, never the provider's reported usage.

## Wanted
Feed the compaction trigger from the provider's last reported prompt tokens when available (already captured in `:turn/request-measured`), or correct the estimate by the observed ratio.
## Acceptance (scenarios TBD)
- A session whose provider-reported usage crosses 80% of the context window compacts on the next turn even when the local estimate is lower.

## Acceptance (Micah approved 2026-10-02; gated)
- Add `usage.gauge_prompt_tokens` to Grover's queued responses (driven-loop path) so a response can declare a first-cycle gauge separate from its reported usage; the "driven loop's declared gauge must not outrank its own reported prompt tokens" scenario then goes red, and the fix (reported usage wins over the declared first-cycle gauge for the compaction stamp) turns it green.
- All @wip scenarios in isaac-agent `features/session/compaction_trusts_reported_tokens.feature` pass with @wip removed (feature-level tag).
- `bb ci` + jvm-spec/jvm-features green.

## Checkpoint (2026-10-02, scrapper@isaac-work-2)

Done: Grover driven-loop gauge separated from reported usage; negative acceptance test failed 85 vs 20 with old priority, then passed with final reported prompt prioritized. @wip removed. `bb ci` green (1863 specs/843 scenarios; one pre-existing pending), `bb jvm-spec` green (1863), focused `bb jvm-features features/session/compaction_trusts_reported_tokens.feature` green (3). Gate PASS against bean branch 6fec114; branch pushed and rebased onto origin/main. Full `bb jvm-features` was RED in two unrelated turn-store scenarios (features/turn/turn_store.feature:161,173), native suite green. Next: retry full JVM features with `ISAAC_TEST_TIMEOUT_MS=600000 bb jvm-features`, isolate any persistent failures, re-run gate, land on main and mark completed if green. Resume at features/turn/turn_store.feature:161.

feature-baseline: isaac-agent 3d99cf35f0255064bcbb5aa0efc1e50caa4e9528
feature-blob: isaac-agent features/session/compaction_trusts_reported_tokens.feature 75edf973c9612ecdaabe9629880cf460abf268cd 27,45,83
