---
# isaac-k2e7
title: "Replace opts map with named turn-request shapes"
status: completed
type: task
priority: normal
created_at: 2026-05-06T22:32:54Z
updated_at: 2026-05-07T00:44:10Z
---

## Description

The opts map at api/dispatch! and api/run-turn! is loosey-goosey: required and optional keys mixed, two unrelated concepts under :channel (Comm instance vs surface type string), Comm-instance buried in opts when it's the callback target.

Replace with two documented shapes:
- inbound-turn-request: {:session-key :input :comm :soul-prepend? :crew? :model-ref?}
  — what the comm hands to api/dispatch!
- resolved-turn: {:session-key :input :comm :model :provider :soul :context-window :boot-files}
  — what bridge hands to drive after resolution

No per-call apron schema validation (cost too high). Shapes documented in code, single producer per shape (comm builds inbound, bridge builds resolved), so well-formedness is structural rather than runtime-checked.

Folds in isaac-dqnc: rename :channel → :comm (Comm instance), and rename key-str → session-key in api/dispatch! and api/run-turn! arglists. Session metadata's :channel string ("discord"/"cli"/"acp") stays as-is since :comm is now unambiguous.

## Acceptance Criteria

api/dispatch! takes a single inbound-turn-request; api/run-turn! takes a resolved-turn; :channel-as-Comm-instance gone; key-str renamed to session-key; :models / :crew-members no longer in any payload; isaac-dqnc superseded; bb spec and bb features green.

## Notes

Verification failed on re-review: bb spec and bb features both pass, api/dispatch! takes a single request map and api/run-turn! takes a single resolved-turn map, but the payload cleanup acceptance is not met. The bead explicitly says ':models / :crew-members no longer in any payload'. In the current implementation, bridge/resolve-turn-opts still emits both keys in the resolved turn (src/isaac/bridge.clj:277-303, especially lines 299-301), and downstream drive code still consumes them (src/isaac/drive/turn.clj:506-532). Producers are also still threading them in request payloads: prompt CLI passes :crew-members and :models into bridge/dispatch! (src/isaac/bridge/prompt_cli.clj:149-159), hooks build turn-opts with :crew-members and :models (src/isaac/server/hooks.clj:102-108), and cron job-context returns both keys before bridge/dispatch! (src/isaac/cron/scheduler.clj:28-36, used at line 43). So the new shapes are only partially enforced and still carry the old lookup tables in payloads.

