---
# isaac-8hpd
title: "ACP comm: namespace move + -isaac-init + AcpChannel → AcpComm + use acp.jsonrpc helpers"
status: completed
type: task
priority: normal
created_at: 2026-05-07T16:44:50Z
updated_at: 2026-05-07T17:57:22Z
---

## Description

Bring ACP into line with the comm-as-module pattern that Discord and Telly follow.

Changes:

1. Namespace move: pull ACP machinery under isaac.comm.acp.*
   - isaac.acp.rpc → isaac.comm.acp.rpc
   - isaac.acp.jsonrpc → isaac.comm.acp.jsonrpc
   - isaac.acp.server → isaac.comm.acp.server
   - isaac.acp.cli → isaac.comm.acp.cli
   - isaac.server.acp-websocket → isaac.comm.acp.websocket (server-side handler; lands here so the comm owns its full surface)
   - isaac.comm.acp stays put (the entry ns)
   - Update all consumers (isaac.server.routes, tests, etc.) to follow.

2. -isaac-init activation hook in isaac.comm.acp:
   (defn -isaac-init [] (api/register-comm! "acp" make))
   Drop any current bare-top-level registration.

3. AcpChannel → AcpComm rename:
   The deftype at isaac.comm.acp:106 still uses 'Channel' as a synonym for the Comm instance. This is the same naming smell flagged in isaac-k2e7 for the :channel opts key. Rename here so the term 'Channel' disappears from comm-instance code paths.

4. Use isaac.acp.jsonrpc helpers consistently:
   isaac.comm.acp lines 12-41 hand-roll {:jsonrpc "2.0" :method "session/update" :params ...} in five fns (text-notification, user-text-notification, thought-notification, available-commands-notification, tool-call-notification). Replace each with calls to the jsonrpc ns helpers (e.g. (jsonrpc/notification "session/update" {...})). Add helpers to the jsonrpc ns if needed.

WS routing is NOT in this bead — it's the topic of the sibling 'HTTP route registry' bead. ACP keeps its current /acp route hard-coded in isaac.server.routes for now and migrates to register-route! when that bead lands.

## Acceptance Criteria

ACP machinery lives under isaac.comm.acp.*; -isaac-init registers the comm; AcpComm replaces AcpChannel; the five hand-rolled jsonrpc shapes go through acp.jsonrpc helpers; bb spec and bb features green.

