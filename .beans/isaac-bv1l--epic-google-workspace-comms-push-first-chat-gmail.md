---
# isaac-bv1l
title: 'Epic: Google Workspace comms — push-first Chat + Gmail on shared isaac-google plumbing'
status: draft
type: epic
priority: high
tags:
    - google
    - comm
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
---

Isaac receives Google Chat messages and Gmail as soon as Google has them, and can speak back on both — the way Discord and iMessage already work. Push-first: Google publishes to one Pub/Sub topic, a push subscription POSTs to an Isaac door, Isaac authenticates, persists, gates deterministically, and only then starts a turn.

## Decisions (2026-09-18, Micah)

- **Identity = the Google user** (e.g. yopp@tonotop.com), not a Chat app. A user-authorized Workspace Events subscription sees every message in every space the account belongs to; people mention that account to get Isaac's attention; DMs to it get a reply. (A Chat app would be a smaller build but only sees mentions/DMs — rejected for now.)
- **Push, with the host exposed publicly** (Funnel, same posture as zanebot). The door is one authenticated path, not the server. Pull stays a later host option.
- **Not hooks.** Hooks go straight to a turn. Everything above the agent here is deterministic (token check, dedupe, echo drop, allow-lists, mention detection, history walking) and lives in a plain route + durable inbox.
- **Three modules.** `isaac-google` (shared plumbing, knows nothing of Chat or Gmail), `isaac-gchat` (comm), `isaac-gmail` (comm). Two comms, not one.
- Host specifics (which account, which spaces, network posture, HIPAA policy) are config/ops, never module code.

## Architecture (anchors in the existing seams)

| piece | module | seam |
|---|---|---|
| OAuth login for the Google user; refresh tokens in the auth store; scopes are the union of what consumers contribute | isaac-google | `isaac.llm.auth.store` (save-tokens!/load-tokens/refresh-oauth-tokens!); new berth `:isaac.google/scopes` |
| Push door `/google/pubsub`: verify Google OIDC token (audience + push service account), unwrap envelope, dedupe on messageId, persist to inbox, 204 in ms | isaac-google | `:isaac.server/route` (as isaac-hooks does), durable records as in `isaac.hail.store` |
| Inbox worker: hands each event to the handler registered for its event type (Chat CloudEvents `ce-type`, Gmail watch data) | isaac-google | new berth `:isaac.google/handler`; `:isaac/component` |
| Registration registry + renewal timer: consumers contribute create/renew/expiry fns | isaac-google | new berth `:isaac.google/registration`; `:isaac/component` |
| Health: last-event age per registration + expiry read from Google; silence raises attention | isaac-google | hail attention pattern (`isaac.hail.attention`) |
| Chat comm: gate → route → dispatch; send to space / DM / thread | isaac-gchat | `:isaac.http/comm` contribution + `isaac.comm.factory/create` defmethod, Discord-shaped |
| Gmail comm: history walk from a durable history id → dispatch; send with threadId | isaac-gmail | same |

Routing vocabulary is the one Discord channels and hooks already use: crew, session, session-tags, reach, prefer, create.

## Children, in order

1. isaac-google skeleton + OAuth login
2. isaac-google push door + durable inbox + handler berth
3. isaac-gchat inbound: gate, routing, dispatch (one space)
4. isaac-gchat outbound: space / DM / thread
5. Chat registrations + renewal component
6. isaac-gmail inbound + outbound
7. Health + attention
8. Pull mode as a host option (parked)

## Open questions carried into the children

- Exact scopes for user-authorized Chat message subscriptions (believed: chat.messages.readonly + chat.spaces.readonly; confirm against current docs in child 1/5).
- Whether non-mention space messages should be recorded into the session as ambient context without a turn (child 3 proposes a `respond` policy: mentions | all | never; ambient recording is out of scope for v1).

## Security alignment with isaac-gym1 (per-principal scoped auth), 2026-09-18

- The push door is **not a bearer-secret principal**: Google signs a per-request OIDC token; Isaac holds no secret to hash. Under gym1 the door must be an **identity source** (the `:isaac.http/identity` berth gym1 names for pluggable sources) that verifies the token and yields principal `{:name :google-pubsub :scopes #{:google/push}}`, and the route declares `:scope :google/push`. Child 2 is blocked by isaac-bzgw for that berth and the route `:scope`; an unscoped door would require `:*` after bzgw and break push.
- gym1 audit (isaac-2a2x) will log `:principal :google-pubsub` on every push and alert on first use — desired.
- OAuth client secret and user tokens never sit in config: `${GOOGLE_CLIENT_SECRET}` from `.env`, tokens in the auth store. Same principle as gym1's hashed secrets.
- Exposure: the door is the reason yopp gets a public Funnel; gym1 parks Funnel scope as a separate question. With per-principal scopes every other route stays admin/scoped behind that exposure.

## Repos (created 2026-09-18)

`slagyr/isaac-google`, `slagyr/isaac-gchat`, `slagyr/isaac-gmail` — public, MIT, scaffolded from isaac-mcp (bb ci, pre-push hook, CI workflows, module skeleton + spec green). `slagyr-assistant` invited with write (pending acceptance).

Source strategy doc dated 2026-09-12, amended 2026-09-16; Micah's architecture session 2026-09-18.
