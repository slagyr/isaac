---
# isaac-0gtc
title: 'isaac-gchat inbound: gate (echo, allow-lists, mention, policy) → route → dispatch for one space'
status: in-progress
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T19:31:53Z
parent: isaac-bv1l
blocked_by:
    - isaac-1jep
---

Isaac reads one Google Chat space and answers when addressed. Discord-shaped comm: `:isaac.http/comm` contribution `{:gchat {:namespace isaac.comm.gchat …}}` + `isaac.comm.factory/create` defmethod; handler contributed to `:isaac.google/handler` for `google.workspace.chat.message.v1.*`.

## The gate (deterministic, before any turn)

1. **Echo drop**: sender == the configured Google user → drop.
2. **Allow-lists fail closed** (iMessage rule): `:gchat/spaces` map of configured spaces; `:gchat/allow-from` sender emails. Not configured = dropped.
3. **Classify**: DM vs space (space type from the pointer fetch).
4. **Mention detection**: the message's annotations mention the account.
5. **Policy** per space: `:respond :mentions` (default) | `:all` | `:never`; DMs `:all` by default.
6. **Route** with the shared vocabulary (crew, session, session-tags, reach, prefer, create) exactly as Discord `:channels` does; thread name rides along in origin so the reply lands in-thread.
7. `bridge/dispatch!` with `:origin {:kind :gchat …}`; the comm's reply path is child 4.

Pointer-only events: the handler fetches the message as the member (`spaces.messages.get`) — needed anyway for text, thread, attachments. Fetch failure = logged, record failed, no turn.

## Scenarios (approved 2026-09-18, Micah) — committed `@wip` in isaac-gchat `d7b63b2` `features/comm/gchat/inbound.feature`

| line | scenario |
|---|---|
| :26 | a mention in a configured space starts a turn on the space's session |
| :39 | a message that does not mention the account starts no turn under the default policy |
| :51 | a DM starts a turn without a mention |
| :66 | Isaac's own message is dropped |
| :79 | unconfigured spaces and unknown senders fail closed |
| :99 | a space with respond policy all answers without a mention |

Config shape pinned by the scenarios (Discord-shaped, `:isaac.http/comm` extra-schema): `comms.gchat.gchat/account`, `comms.gchat.gchat/allow-from` (seq of emails, fail closed), `comms.gchat.gchat/spaces.<space-name>.{name crew session session-tags reach prefer create respond}` with `respond` ∈ mentions (default) | all | never. Session default name `gchat-<space name with / → ->` e.g. `gchat-spaces-ENG`. Log events: `:gchat/message-routed` (info; `:space :thread :session`), `:gchat/message-dropped` (debug; `:reason :self|:space|:sender|:no-mention|:policy`), `:gchat/fetch-failed` (error).

## Step ledger

| step | status |
|---|---|
| default Grover setup in … / config: / the following model responses are queued: / session … has transcript matching: / the session count is … / grover records zero provider requests / the log has entries matching: | reuse (agent, foundation) |
| **the Chat API returns message {name}:** | **NEW — stubs `spaces.messages.get` for that name; rows: sender.email, thread.name, text, annotations.mention (user resource of the mentioned account), space.type (default SPACE; DIRECT_MESSAGE for DMs)** |
| **Google Chat delivers a message event for {name}** | **NEW — builds a pointer event (`google.workspace.chat.message.v1.created`, `message.name`) and hands it to the handler this module contributed to `:isaac.google/handler`, synchronously; awaits the turn like Discord's MESSAGE_CREATE step** |

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-gchat && bb features features/comm/gchat/inbound.feature:26
cd isaac-gchat && bb features features/comm/gchat/inbound.feature:39
cd isaac-gchat && bb features features/comm/gchat/inbound.feature:51
cd isaac-gchat && bb features features/comm/gchat/inbound.feature:66
cd isaac-gchat && bb features features/comm/gchat/inbound.feature:79
cd isaac-gchat && bb features features/comm/gchat/inbound.feature:99
cd isaac-gchat && bb ci
```

Unit specs for: the gate as a pure function (event + config → route | drop reason), mention detection over annotations, session naming.

Ambient recording of non-mention messages (context without a turn) is NOT in v1 — noted in the epic as an open question.

Dispatched: hail e04e4800 2026-09-18T19:33:38Z (band isaac-work)
