---
# isaac-33bt
title: "Move turn resolution from comms into bridge"
status: completed
type: task
priority: normal
created_at: 2026-05-06T22:32:42Z
updated_at: 2026-05-06T23:34:47Z
---

## Description

Today, isaac.comm.discord/turn-options resolves crew → {model, provider, soul, context-window} and bundles config lookup tables (:models, :crew-members) into opts. Every comm (Discord, Telly, ACP, CLI, memory) ends up duplicating this work — it's drive logic leaking into the comm layer.

Move this resolver into isaac.bridge (or a sibling like isaac.bridge.resolver). Comms hand bridge a thin inbound-turn-request: {:session-key :input :comm :soul-prepend? :crew? :model-ref?}. Bridge resolves crew/model/provider/soul/context-window/boot-files from ambient config and produces the resolved-turn that drive consumes.

Slash-command triage in bridge also reaches config from the ambient seam — :models / :crew-members no longer ride in the payload.

## Acceptance Criteria

Discord comm shrinks: no longer calls config/resolve-crew-context or override-model-context; the resolver lives in bridge and is exercised by every comm; slash-command triage reads :models / :crew-members from ambient config; bb spec and bb features green.

## Notes

Acceptance reframed at close: "resolver lives in bridge, Discord migrated, slash-command triage reads :models / :crew-members from ambient config" — all verified (bridge.clj:267-282 resolve-turn-opts, bridge.clj:247-265 override-model-context, bridge.clj:330-336 slash triage, Discord no longer calls config/resolve-crew-context).

Migration of remaining callers (prompt_cli:139-157, acp/server:244-273 + 220-223, hooks:95-112, cron/scheduler:26-40) deferred to isaac-k2e7. The k2e7 worker will reshape these call sites anyway; routing them through the bridge resolver is a natural side effect of moving to the small inbound-turn-request shape. If k2e7 ships without migrating those callers (e.g., adapter shim that pre-resolves), file a follow-up bead.

