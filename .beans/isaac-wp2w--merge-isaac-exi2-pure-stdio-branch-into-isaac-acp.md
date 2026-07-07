---
# isaac-wp2w
title: Merge isaac-exi2 pure-stdio branch into isaac-acp main, reconciled with the gnji root fix
status: in-progress
type: task
priority: high
tags:
    - unverified
created_at: 2026-07-07T16:11:28Z
updated_at: 2026-07-07T16:23:36Z
---

The verified isaac-exi2 work (branch isaac-exi2-pure-stdio, commit a8b26e6f) predates main gaining 3b48d97 (isaac-gnji: honor top-level root in acp cli). A naive merge compiles but its green-ness cannot be adjudicated outside a pinned-siblings environment. Task: merge the branch into main, reconcile with the gnji root-honoring change, prove bb spec + bb features green under proper sibling pinning (per the repo CI convention), push, and note the merge commit here. Do NOT re-verify exi2 itself — it passed; this is integration only. After the merge lands, the acp module gets a version bump + deploy (planner handles the train).

## Implementation Notes

- Merged `origin/isaac-exi2-pure-stdio` into `isaac-acp` `main`; merge commit: `78348b5` (`Merge isaac-exi2 pure-stdio into main, reconciled with gnji root fix`).
- The merge cleanly reconciled the only concurrent mainline change: `13ac636` / `3b48d97` top-level `--root` fix in `src/isaac-manifest.edn` version line.
- Verification in `isaac-acp` must use the repo's pinned-deps mode, not sibling override mode. Local sibling auto-detection (`bb spec` / `bb features` without `ISAAC_GIT=1`) pulled ahead-of-pin sibling repos and produced unrelated ACP server/tooling failures.
- Authoritative integration proof run:
  - `ISAAC_GIT=1 bb spec` -> 0 failures, 1 pending
  - `ISAAC_GIT=1 bb features` -> 0 failures, 5 pending
- Pushed `isaac-acp` `main` to `origin` at `78348b5`.
- Top-level `isaac/modules.edn` still pins `:isaac.comm.acp` to pre-merge SHA `13ac636fda2073d0ac9b81d5c97ccbd29a535bb6`; module bump / deploy remains planner-train follow-up per bean scope.
