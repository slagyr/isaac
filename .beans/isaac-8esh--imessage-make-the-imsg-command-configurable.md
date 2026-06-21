---
# isaac-8esh
title: imessage make the imsg command configurable
status: in-progress
type: feature
priority: normal
created_at: 2026-06-21T01:20:32Z
updated_at: 2026-06-21T01:44:20Z
---

Today isaac-imessage only exposes :imessage/bin as a string path and always spawns [bin "rpc" ...]. That is too narrow for wrapper-based deployments.

We validated on zanebot that OpenClaw-style stdio transport is viable from another host:
- ssh zane@zanebot.tail66e5f8.ts.net /usr/local/bin/imsg send ... succeeded
- JSON-RPC over stdio through ssh to imsg rpc also succeeded

Need a new iMessage config that can express the full imsg launch command, not just the executable path, so operators can use wrappers like ssh -T <host> /usr/local/bin/imsg while keeping the existing direct local default.

Requirements:
- Preserve current behavior when no new config is set
- Support a transparent stdio wrapper for long-lived imsg rpc
- Decide whether to replace or augment :imessage/bin; keep backward compatibility explicit
- Define how :imessage/db-path interacts with local vs wrapped/remote command modes
- Update schema, runtime docs, and focused specs

Done when isaac can be configured to launch imsg through a wrapper command without code hacks, and the config contract is documented clearly enough for remote-Mac deployments.
