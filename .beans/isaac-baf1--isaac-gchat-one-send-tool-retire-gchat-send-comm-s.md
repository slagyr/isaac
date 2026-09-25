---
# isaac-baf1
title: 'isaac-gchat: one send tool — retire gchat__send, comm__send is the send, guidance carries the response/send distinction, mw27 guard out'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T04:21:09Z
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
