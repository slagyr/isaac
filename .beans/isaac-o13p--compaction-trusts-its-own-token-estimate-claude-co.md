---
# isaac-o13p
title: Compaction trusts its own token estimate; claude-code context reached ~1M tokens
status: draft
type: bug
priority: high
created_at: 2026-10-02T04:41:34Z
updated_at: 2026-10-02T04:41:34Z
---

Found 2026-10-02 on yopp, ACP session acp-2026-09-29-1639-540a (model claude-sonnet → claude-sonnet-5 via :claude-code, :context-window 1000000, :context-mode :full, rubberband, threshold 0.8). Every cycle `:turn/request-measured` showed estimated ~311K vs provider-reported ~500K (ratio ~1.61), while `:session/compaction-check` sat at ~502K (under the 800K trigger). The 04:24:08Z request reported prompt-tokens 1,004,210 (cache-write 929,288 — a cache miss). Likely also why the Claude subscription limit was hit.

isaac-agent compaction.clj `should-compact?` (116-118) only sees `compaction/context-gauge` → `prompt-builder/estimate-tokens`, never the provider's reported usage.

## Wanted
Feed the compaction trigger from the provider's last reported prompt tokens when available (already captured in `:turn/request-measured`), or correct the estimate by the observed ratio.
## Acceptance (scenarios TBD)
- A session whose provider-reported usage crosses 80% of the context window compacts on the next turn even when the local estimate is lower.
