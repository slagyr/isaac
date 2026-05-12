---
# isaac-c4k2
title: "Move isaac.acp.ws -> isaac.util.ws-client"
status: completed
type: task
priority: low
created_at: 2026-04-30T05:22:23Z
updated_at: 2026-04-30T12:20:31Z
---

## Description

isaac.acp.ws is used as a WebSocket CLIENT by both the ACP outbound
proxy AND the Discord gateway adapter. Neither use is server-side.
The 'acp' in its current namespace is misleading — initial readers
assume it's an ACP-protocol module.

## Move

src/isaac/acp/ws.clj          -> src/isaac/util/ws_client.clj
spec/isaac/acp/ws_spec.clj    -> spec/isaac/util/ws_client_spec.clj

Namespace: isaac.acp.ws -> isaac.util.ws-client

Rationale for isaac.util.*: generic utilities, layer-agnostic.
Already houses isaac.util.shell. Naming pushed back on
isaac.transport.* (over-engineered for the current scope) and
isaac.server.* (the wrapper is a client, not server-side).

The '-client' suffix is intentional: the wrapper is purely a
client; the suffix removes the ambiguity that initially read
as a server module.

## Update callers

- isaac.acp.* — all callers in the ACP proxy stack
- isaac.comm.discord.gateway — newer caller after Discord wiring

## Why now

After isaac-6bfz (lifecycle fix) lands. A pure rename in a
follow-up commit keeps the diffs clean.

## Definition of done

- isaac.acp.ws no longer exists
- isaac.util.ws-client is the new home
- All callers updated, specs pass
- bb features and bb spec green

## Related

- isaac-6bfz: fix lifecycle gaps in the ws wrapper (do first;
  bug fix lands in the existing namespace).

