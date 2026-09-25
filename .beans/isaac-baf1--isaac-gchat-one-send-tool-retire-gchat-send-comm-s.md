---
# isaac-baf1
title: 'isaac-gchat: one send tool — retire gchat__send, comm__send is the send, guidance carries the response/send distinction, mw27 guard out'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T04:40:52Z
---

## Why (Micah, 2026-09-25)

Three send tools overlapped on a yopp turn: comm__send (generic, queue-first,
retries, per-comm send-schema), gchat__send (a second, direct-API path to the
same post) and gmail__send. The model guessed, posted its answer with the tool,
and the comm posted the answer again. Ruling: **agents keep all their tools;
nothing is hidden per turn** (isaac-7mwt rejected). Instead there is ONE send
tool, comm__send, and the guidance makes the response/send distinction
unmistakable. The isaac-mw27 dedupe guard is a heuristic ("a send into the
current thread means no response is needed") that misfires — "count to 10, one
message each, in this channel" — and comes out.

## Design

- Retire `gchat__send`: drop the `:gchat__send` entry from the manifest
  `:isaac.agent/tools` berth and `send-tool-factory` from tools.clj.
  `gchat__history` and `gchat__spaces` stay. comm__send already carries
  gchat's send-schema (`gchat.space`, `gchat.to`, `gchat.thread`).
- Remove the isaac-mw27 guard: the replied-via-tool mark in `on-tool-call*`,
  the skip in `on-reply*`, the `:gchat/reply-deduped` log, their specs and
  the two mw27 scenarios in outbound.feature (replaced by this bean's).
- `guidance/TEXT` keeps the thread-marker paragraph and the tool-trace
  sentence; the sending paragraph becomes exactly:

  > Your response is the text you end this turn with. It is delivered back over the channel this message came from, so never send it with comm__send. That tool is for additional messages of your own during the turn: several messages in a row, a message to another thread, space or person, or something you were asked to send. Those never replace your response, so still end the turn with it, even if it is short.

## Acceptance (features/comm/gchat/outbound.feature — baselined)

- [ ] Scenario "comm__send into the origin thread during the turn, then the
  answer — both post (isaac-baf1)": the tool's delivery and the reply both reach
  Chat, in that order.
- [ ] Existing "gchat/reactions false turns the lifecycle off, the reply still
  posts once" keeps passing (text-only turn → one post).
- [ ] One-time: `gchat__send` is no longer registered (module_spec lists the
  tool set: history, spaces); guidance_spec asserts the paragraph above
  verbatim; `:gchat/reply-deduped` appears nowhere in src.
- [ ] Version bump; bb spec / bb features / bb lint green.

Deploy note: yopp crew allow `:gchat/*` needs no change.

Likely repo scope: isaac-gchat (manifest, tools.clj, gchat.clj, guidance.clj,
specs, outbound.feature).

feature-baseline: isaac-gchat e6c78a153be8a57f670666eadf162b61271e36e3
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 5b9541244161bcbbefd112f44a610170cb155f47 489

## Conflict (scrapper@isaac-work-1, 2026-09-25)

Implementation is done and pushed on `bean/isaac-baf1` in two repos (not landed):
- **isaac-gchat** (bean/isaac-baf1): gchat__send retired (manifest + tools.clj), the mw27 guard, log and specs removed, guidance paragraph verbatim, version 0.2.12, `@wip` off the baf1 scenario. Feature steps now hold the Chat http+token seams across steps. `bb spec` green (180/0).
- **isaac-agent** (bean/isaac-baf1): (1) `comm_send/impl-keyword` threw NPE for a comm slot with no `:type`, which aborted the whole turn (`build-turn` → `ensure-policy-tools!`). It now uses `isaac.comm.factory/impl-id` (`:type`, else the slot id), with a spec. (2) The `the delivery worker ticks` step replaced the comm registry with stubs only, so a live gchat comm was unreachable. It now layers the stubs over the live instances. `bb ci` green.

With both fixes, the baselined scenario sends 2 posts to spaces/OS1/messages (count step passes). It still fails at outbound.feature:505:

    And an outbound HTTP request to ".../spaces/OS1/messages" matches:
    Expected: []  got: ["body.text: Expected \"Looking now.\", got: \"All green.\""]

It can't pass as written:
1. Both `matches:` tables leave out `#index`, so `outbound-http-request-to-url-matches` checks request **0** for both. One request can't have text "Looking now." and "All green." at once.
2. The acceptance says "tool's delivery and the reply … in that order". comm__send is queue-first: the send is enqueued during the turn and delivered at `the delivery worker ticks`. By then on-reply has already posted "All green.", so the real order is reply, then send.

Needs a planner decision: add `#index` rows (0 = All green., 1 = Looking now.) and restate the order as "both post", or say how the tool send should be delivered before the reply.

feature-baseline: isaac-gchat 00fd113a67ad8629b237a3f0f67055bed7cc1ecf
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 8d57019503697c5d525dc59bccffd21c2d671a4b 490



## Planner adjustment (2026-09-25, prowl@isaac-plan) — both post; reply then queue-first tool send

Conflict: the baselined scenario cannot pass. (1) Both `matches:` tables omit `#index`, so both check request 0. (2) `comm__send` is queue-first: the reply posts during the turn; the tool delivery posts at `the delivery worker ticks`. Actual order is reply, then send. Do **not** change the queue-first product.

**Decision: re-baseline the order.** `#index` 0 = `All green.` (the reply). `#index` 1 = `Looking now.` (the tool delivery). Acceptance "in that order" is restated: both post; the reply is first because the send is queued.

isaac-gchat main `00fd113`. New baseline (in force):

    feature-baseline: isaac-gchat 00fd113a67ad8629b237a3f0f67055bed7cc1ecf
    feature-blob: isaac-gchat features/comm/gchat/outbound.feature 8d57019503697c5d525dc59bccffd21c2d671a4b 490

### Worker now

1. Rebase `bean/isaac-baf1` (gchat) onto origin/main `00fd113`. Keep implementation. Worker `.feature` diff may only drop `@wip`.
2. Confirm the baf1 scenario green (2 posts, `#index` 0 All green., `#index` 1 Looking now.).
3. Keep the agent fixes (impl-id, tick keeps live comms). Land agent then gchat per pin rule, or hand verifier if ungated on agent.
4. Do not recut queue-first. Do not restore gchat__send or the mw27 guard.

This note resets the verify-fail counter.
