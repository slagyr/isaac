---
# isaac-vlxz
title: 'isaac-gchat: attachments — upload to Chat, then post the message referencing them'
status: completed
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T04:53:47Z
blocked_by:
    - isaac-o9h4
---

## Why

comm__send attachments (agent bean) need each comm to upload. Google Chat
takes a media upload first, then the message references it.

## Design

- Manifest comm entry `:send-attachments? true`.
- `send!*` with `:attachments`: for each path, POST multipart to
  `https://chat.googleapis.com/upload/v1/<space>/attachments:upload`
  (filename, content type by extension), collect the returned
  `attachmentDataRef`/upload token, then post the message with
  `attachment: [{attachmentDataRef …}]` alongside `text`. One failed upload
  fails the delivery (worker retries / dead-letters as today) — no partial
  message.

## Acceptance (features/comm/gchat/outbound.feature — baselined)

- [ ] "comm__send with an attachment uploads it, then posts the message
  referencing it (isaac-vlxz)": 1 upload request per file, then 1 message request
  whose body carries the attachment ref and the text.
- [ ] Version bump; bump the isaac-agent pin to the sha carrying
  `attachments`; bb spec / features / lint green.

Likely repo scope: isaac-gchat (gchat.clj, chat_api.clj, manifest, feature).

feature-baseline: isaac-gchat e6c78a153be8a57f670666eadf162b61271e36e3
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 5b9541244161bcbbefd112f44a610170cb155f47 517



## Planner note (2026-09-25)

isaac-o9h4 landed: pin `io.github.slagyr/isaac-agent` to `ff62ae76b18eb41cde12ba9f5bcb716ddd41ce9f` (0.1.83) in deps.edn — it carries comm__send `attachments` and the `:send-attachments?` manifest flag. Declare `:send-attachments? true` on this comm's manifest entry.

## Worker note — conflict (2026-09-25, scrapper@isaac-work-1)

Done, pushed on `bean/isaac-vlxz` (not landed):
- **isaac-gchat** `bean/isaac-vlxz` @ 42b7b1a: `isaac.comm.gchat.attachment` (upload-all!, content type by extension), `chat-api/upload-attachment!` (multipart/related POST to `/upload/v1/<space>/attachments:upload?uploadType=multipart`, returns attachmentDataRef, throws on non-2xx), `create-message!` carries `attachment: [{attachmentDataRef …}]`, send!* uploads all before posting (one failure = no message, transient failure), refs ride the first chunk. Manifest `:send-attachments? true`, version 0.2.15, isaac-agent pin ff62ae7 + foundation eaea445. Upload stub in gchat_steps. Cherry-picked baf1's 5357e05 (steps hold Chat http+token seams). bb spec green.
- **isaac-agent** `bean/isaac-vlxz` @ a940dbe: ported baf1's bdc0405 (comm__send impl = :type else slot id — the gchat slot has no :type, NPE'd the turn) and cherry-picked 8832eda (delivery-worker tick layers stubs over live comms); `a file … exists in the session working directory` step now falls back to user.dir when no session exists yet (a comm-created session gets the store default cwd).

With those, the baselined scenario uploads (count step passes) but fails at the match step:

    And an outbound HTTP request to ".../v1/spaces/AT1/messages" matches:
    got: ["body.text: Expected \"Here is the report.\", got: \"Sent.\""
          "body.attachment.0.attachmentDataRef.resourceName: Expected match for (?s).+, got: nil"]

The match step checks request **#0** when no `#index` row is given. comm__send is queue-first: on-reply posts "Sent." at turn end, and the attachment message posts at `the delivery worker ticks` — request #1. This is the same defect baf1 reported. `bb bean-gate verify` = PASS (text only), but the acceptance scenario is red, so this can't land.

Planner decision needed: add `| #index | 1 |` to the match table (under baf1's "both post"), or say how the send should come before the reply.

feature-baseline: isaac-gchat 5a2a2a4b70d351043595772d994ec1cef797cd04
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 292e54a6a85070f2973bd2418789faa4445cc135 520



## Planner adjustment (2026-09-25, prowl@isaac-plan) — attachment message is request #1

Same defect as isaac-baf1. `comm__send` is queue-first. The reply `"Sent."` is request #0. The attachment message (`"Here is the report."` + attachment ref) posts at `the delivery worker ticks` and is request #1. Do **not** change queue-first.

**Decision: add `| #index | 1 |` to the messages match table.** Upload count step stays (1 upload, then the message).

isaac-gchat main `5a2a2a4`. New baseline (in force):

    feature-baseline: isaac-gchat 5a2a2a4b70d351043595772d994ec1cef797cd04
    feature-blob: isaac-gchat features/comm/gchat/outbound.feature 292e54a6a85070f2973bd2418789faa4445cc135 520

### Worker now

1. Rebase `bean/isaac-vlxz` (gchat) onto origin/main `5a2a2a4`. Keep implementation. Worker `.feature` diff may only drop `@wip`.
2. Confirm the vlxz scenario green (1 upload, message match at `#index` 1).
3. Keep the agent test-infra fixes. Do not recut queue-first.
4. Hand to verifier or gated close as the gate says.

This note resets the verify-fail counter.

## Landed on main (2026-09-25)

Rebased onto isaac-gchat 5a2a2a4 (the #index 1 baseline). The vlxz scenario passes: 1 upload, message match at #index 1. Only change to the .feature: @wip dropped. Queue-first left as it was. The agent test-infra fixes bdc0405/8832eda went in with baf1's agent part (372b7de); the user.dir fallback for the session-workdir file step landed separately. The gchat squash includes baf1's step seams (Chat http+token held across steps), because this scenario needs them.
isaac-agent bb ci green (1792 specs, 872 features). isaac-gchat bb ci green (198 specs, 56 features). bb bean-gate verify PASS on the squash commit.

main-sha: isaac-agent 30c9a9dff33e7ecf09ac0bd75132bbbd03b1c229
main-sha: isaac-gchat 2cb19075099a6c48869473c50c8fa0d9288a5019
