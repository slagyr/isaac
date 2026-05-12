---
# isaac-dqnc
title: "Rename :channel → :comm; key-str → session-key in api/bridge/turn surface"
status: completed
type: task
priority: normal
created_at: 2026-05-06T21:57:57Z
updated_at: 2026-05-06T22:33:25Z
---

## Description

The opts key :channel holds a Comm instance, but session metadata :channel holds a surface-type string ("discord"/"cli"/"acp"). Same keyword, two unrelated concepts. The Comm protocol param was already renamed from ch → comm, so the opts/turn-ctx key is the holdout.

Also rename the positional 'key-str' arg in isaac.api/run-turn! and isaac.api/dispatch! (and their downstream sites) to 'session-key' for consistency with the rest of the codebase.

Scope:
- src/isaac/api.clj (run-turn!, dispatch! arg name)
- src/isaac/bridge.clj
- src/isaac/bridge/prompt_cli.clj
- src/isaac/server/hooks.clj
- src/isaac/drive/turn.clj
- src/isaac/acp/server.clj
- modules/isaac.comm.discord/{src,spec}
- src/isaac/comm/memory.clj, src/isaac/comm/acp.clj (if any)
- All specs that thread :channel through opts

Decision needed: session metadata :channel ("discord"/"cli"/"acp") — leave as-is, or rename to :surface / :transport? Keeping :channel in metadata is fine if :comm is unambiguous everywhere else.

## Acceptance Criteria

opts and turn-ctx use :comm for the Comm instance everywhere; api.clj run-turn!/dispatch! arg renamed key-str → session-key; session metadata key decision recorded; bb spec and bb features green.

## Notes

Validation failed on re-review: bb spec and bb features both pass, api.clj and bridge.clj use session-key and :comm as intended, and the comm implementations look updated. However, the turn surface rename is incomplete. src/isaac/drive/turn.clj still exposes run-turn! as [state-dir key-str input opts] (lines 647-656), and the surrounding turn-path helpers in the same file still use key-str throughout (for example build-turn-ctx at lines 505-531, finish-turn! at 533-535, run-turn-body! at 616-638). The bead title and scope explicitly call out the api/bridge/turn surface and downstream sites for key-str -> session-key consistency, so the rename is only partial.

