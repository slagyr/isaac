---
# isaac-7dhm
title: "Crew tools reach every comm path"
status: completed
type: bug
priority: high
created_at: 2026-04-23T01:32:44Z
updated_at: 2026-04-23T02:00:37Z
---

## Description

The :tools.allow map configured on a crew must flow into every turn, regardless of which comm path drives it.

Currently: cli/server.clj and the /acp WebSocket layer never populate :agents in server opts, and cli/acp.clj (stdio) passes :cfg but acp/server.clj:session-prompt-handler uses the raw agents arg (= {}). In both paths crew-members reaches turn.clj as {}, allowed-tool-names returns nil, and the tool-registry default-denies every tool. The model is offered zero tools even when main.edn lists them.

Fix: in acp/server.clj (and/or the comm layer that calls into it), derive :crew-members from :cfg when no agents map is injected. The cli/prompt.clj and comm/discord.clj paths already do this correctly — align stdio ACP and HTTP/WebSocket ACP with them.

Spec: features/bridge/tool_visibility.feature

Acceptance:
1. Remove @wip from every scenario in features/bridge/tool_visibility.feature
2. bb features features/bridge/tool_visibility.feature passes
3. bb features and bb spec pass

## Notes

Implemented crew tool propagation for ACP prompt handling from cfg-backed requests, enabled positional prompt text for the prompt CLI path used by the approved feature, and fixed feature harness coverage for Discord/live server ACP. Verified with bb spec, bb features features/bridge/tool_visibility.feature, and bb features-all features/bridge/tool_visibility.feature. Full bb features still has unrelated failures tracked in isaac-co0f and isaac-m2vc.

