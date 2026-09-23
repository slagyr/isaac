---
# isaac-qry7
title: 'isaac-gchat: a DM where the account is only invited (message request pending) gets a 403 on reply — join it, and request chat.spaces.create for Yopp-initiated DMs'
status: in-progress
type: bug
priority: high
tags:
    - gchat
    - google
created_at: 2026-09-23T03:02:50Z
updated_at: 2026-09-23T16:17:12Z
---

## Observed (yopp, 2026-09-23 02:59Z, gchat 0.2.3)

Micah's DM arrived via spaces/-, the lookup fell back to spaces.list (isaac-f4ab), the turn ran on gchat-tonotop-dm-micah-martin and the model answered — then the reply's messages.create returned 403 and the turn ended :error. Probes with the same token: spaces.get, members.list and messages.create on the DM all 403 'Permission denied … or the resource doesn't exist'; messages.list and spaces.list work; findDirectMessage returns the DM with membershipCount.joinedDirectHumanUserCount = 1. Yopp's side of the DM is a pending message request (the account has never opened Chat), and an invited member receives events but cannot read or post. spaces:setup as Yopp answered 'insufficient authentication scopes': the login never requested chat.spaces.create, so gchat.clj's outbound find-direct-message → setup-direct-message path has never been able to run either.

## Change

1. Login scope: add https://www.googleapis.com/auth/chat.spaces.create to gchat's :isaac.google/scopes contribution (needed for spaces:setup, i.e. Yopp-initiated DMs via comm__send to a person).
2. Inbound DM the account is not joined to: detect it (findDirectMessage's joinedDirectHumanUserCount < 2, or the first 403 on the space) and try spaces:setup with the other member; if Google joins the account that way, proceed; if it does not, log :gchat.dm/invited once with the space uri and the operator action (open Chat as the account and accept), and deliver the reply through the fallback comm (attention) instead of ending the turn :error.
3. A reply that fails 403 is reported as a delivery failure with the space and the reason, not a bare 'Chat API create failed: 403'.

## Verify live

Whether spaces:setup joins an already-invited DM is unknown — probe on yopp once the scope is granted; the outcome decides whether step 2 is 'join' or 'log and hand to the operator'.

## Related

isaac-f4ab, isaac-ihuc, the yopp rollout record (engineering/yopp/google-rollout.md, 2026-09-23).

## Probe results (2026-09-23 03:2xZ, gchat 0.2.4, nine scopes incl. chat.spaces.create)

- spaces:setup as Yopp with Micah as member → 200, returns the existing DM; membershipCount.joinedDirectHumanUserCount stays 1; spaces.get still 403. Setup does NOT join an invited DM.
- members.create for the calling user → 403 insufficient scopes (would need https://www.googleapis.com/auth/chat.memberships, the write scope). Unproven whether it joins a DM; members.get / patch on the own membership → 404 (member name form or not visible while invited).
- Likely cause of the invited state: yopp@ has never opened Google Chat, so its DM memberships are pending until the first Chat sign-in (same organization, so not a message-request policy).

Next: (1) one-time — sign in to Chat as yopp@ once and open the DM; (2) for the future, add chat.memberships (write) to the scope union and try members.create (self) on an invited DM; if that joins, wire it into the inbound path; if not, log :gchat.dm/invited once and deliver via the attention comm. Reply 403s must surface as delivery failures either way.

## Definitive (2026-09-23 15:06Z, ten scopes incl. chat.memberships write)

members.create for the account's own membership in the invited DM → 400 INVALID_ARGUMENT: "Can't create memberships in direct messages between human users or with an app." spaces:setup returns the existing DM without joining it. There is no Chat API path for the account to accept a chat request.

Systemic answer (Micah, 2026-09-23): the Workspace admin setting Google Chat → Chat invitations → **On** ("automatically accept chat invitations from people in your organization") was turned on for tonotop.com. New internal DMs to Yopp are joined without anyone acting. Requests that predate the setting stay pending until accepted once in the account's Chat UI.

Remaining scope for this bean: (1) detect the invited state (spaces.get 403 with joinedDirectHumanUserCount 1 from findDirectMessage) and log `:gchat.dm/invited` ONCE per space with the space uri and the operator action; (2) do not run the model against a DM the account cannot answer — or run it and deliver the reply via the attention comm, naming the DM; (3) a reply 403 surfaces as a delivery failure with space + reason, never a bare create-failed. The chat.memberships write scope added in gchat 0.2.5 is not needed for this and can be dropped in the next scope round.

## Handoff (worker, 2026-09-23)

Implemented the rescoped invited-DM detection + reply-failure reporting in isaac-gchat, branch `bean/isaac-qry7` (pushed, not landed — repo has no bean-gate; awaiting `/verify`). Base: origin/main 6a7a3ba (0.2.6); this branch bumps the manifest to 0.2.7. `bb lint` (src) clean, `bb spec` 136/136, `bb features` 39/39 (whole suite), `bb ci` 0 failures.

**Detection rule** (`src/isaac/comm/gchat/lookup.clj`, `ask!`): after `spaces.get` throws, fall back to the account's `spaces.list` (isaac-f4ab's existing path). If that exception's `ex-data` carries `:status 403` **and** the listing still names the space with `spaceType "DIRECT_MESSAGE"`, mark it `:invited? true` on the returned info map. Explicitly does **not** use findDirectMessage's `joinedDirectHumanUserCount` (proven wrong in the "Definitive" note above — stays 1 after acceptance). A non-403 refusal, or a 403 on a non-DM room, is not invited. `space-info`'s existing per-space memoization means `ask!` (and therefore the warn) fires at most once per space for the process lifetime — no separate "already warned" tracking needed.

**Log/notice text (verbatim)**:
- `:gchat.dm/invited` (warn, once per space) — fields `:space`, `:uri` (`https://chat.google.com/dm/<id>`), `:action`: `"open Chat as the account and accept the request; or turn on Workspace Admin → Google Chat → Chat invitations"`.
- `:gchat.dm/reply-diverted` (warn, every diverted reply) — `:space`, `:thread`, plus `:dropped true` when no `:attention :notify` comm is configured (reply is dropped, not enqueued).
- `:gchat/delivery-failed` (error, any reply `create-message!` failure — 403 in an invited DM or a joined room) — `:space`, `:thread`, `:status`, `:reason` (never the bare `"Chat API create failed: 403"` ex-info message).
- `:gchat/turn-notice` (warn, at `on-turn-end` when a reply failed for that session) — `:class :delivery-failure`, `:session`, `:space`, `:thread`, `:status`, `:reason`. Picked `:class :delivery-failure` per the rescope's "coordinate wording with isaac-h5v8" instruction; isaac-h5v8 (on-turn-end failure/weather notices) had not landed as of this branch, so there was no existing notice API to integrate with — this is a self-contained atom + log in `gchat.clj` (`delivery-failures*`), not a shared mechanism. Worth reconciling once isaac-h5v8 lands.

**How the reply is diverted**: `gchat.clj`'s `on-reply*` checks `:invited?` on the session's stored origin (threaded through from `lookup/space-info` → `handler/decide-opts` → `handler/-origin` → `on-cycle-start*`'s existing origin capture — no new plumbing needed there). When invited, it does **not** call `create-message!`; instead reads `[:attention :notify]` off the *whole* process config (`loader/snapshot`, not the comm's own slice) — the same `{:comm :target}` shape `isaac.attention` reads internally — and hands the reply to `isaac.comm.delivery.queue/enqueue!` directly (isaac.attention itself has no public generic "notify with arbitrary content" fn to call cross-repo). Content is prefixed with one line: `"Pending Chat invite: the DM with <sender> (<space>) has not been accepted yet, so this reply could not be posted there."` then `\n\n` then the turn's actual reply text.

**Files**: `src/isaac/comm/gchat/lookup.clj`, `src/isaac/comm/gchat/handler.clj`, `src/isaac/comm/gchat.clj`, `feature-steps/isaac/gchat_steps.clj` (new stub-http! branches + steps: `the Chat API refuses spaces.get for "<space>" with 403`, `the Chat API refuses messages.create in "<space>" with 403`, plus a `spaces.list` stub), `features/comm/gchat/inbound.feature`, `features/comm/gchat/outbound.feature`, specs under `spec/isaac/comm/gchat*`.

**Scenario counts**: 3 new feature scenarios (2 inbound + 1 outbound, all passing): invited DM → one `:gchat.dm/invited` warn, zero POSTs to the DM, reply lands in `comm/delivery/pending` with the prefix; a second message to the same invited DM → still exactly 1 `:gchat.dm/invited` log entry; a reply 403 in a joined (non-invited) room → `:gchat/delivery-failed` logged with all four fields. Plus unit specs in `lookup_spec.clj` (7 new), `handler_spec.clj` (2 new), `gchat_spec.clj` (4 new) covering the no-attention-comm drop path and the on-turn-end notice/clear, which have no feature-level scenario.

Left `in-progress`, no tags — next is `/verify`.
