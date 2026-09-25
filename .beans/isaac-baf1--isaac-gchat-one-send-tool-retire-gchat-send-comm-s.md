---
# isaac-baf1
title: 'isaac-gchat: one send tool — retire gchat__send, comm__send is the send, guidance carries the response/send distinction, mw27 guard out'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T05:05:03Z
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
  answer — both post (isaac-baf1)": both reach Chat — the response at turn
  end (request 0), the queued send when the delivery worker ticks (request 1).
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

feature-baseline: isaac-gchat 00fd113a67ad8629b237a3f0f67055bed7cc1ecf
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 8d57019503697c5d525dc59bccffd21c2d671a4b 490


## Planner decision (2026-09-25 04:50Z) — re-baselined

Worker's conflict note accepted: comm__send is queue-first, so the response posts at turn end and the send lands at the tick. Scenario now carries #index 0 = All green., #index 1 = Looking now.; the contract is that both post. Re-baselined on isaac-gchat main; rebase bean/isaac-baf1, drop @wip again, re-run the gate.

feature-baseline: isaac-gchat 00fd113a67ad8629b237a3f0f67055bed7cc1ecf
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 8d57019503697c5d525dc59bccffd21c2d671a4b 490



## Planner note (2026-09-25 05:20Z) — agent part landed

Your isaac-agent branch bean/isaac-baf1 is squash-landed on isaac-agent main as `372b7debf6f8fe5167581d873e04c31818f5b6c3` (0.1.84). Pin isaac-gchat deps.edn to that sha (drop the pre-squash pin), rebase, re-run the gate, land the gchat half. The agent branch will be deleted after the gchat landing.

## Conflict (scrapper@isaac-work-2, 2026-09-25) — gate FAIL on a scenario baf1 did not touch

Done:
- **isaac-agent**: agent fixes (impl-id, tick keeps live comms) already on main as 372b7de (0.1.84, Micah's squash). Worker rebase onto o9h4 matched it exactly. Agent side is landed; main-sha isaac-agent 372b7debf6f8fe5167581d873e04c31818f5b6c3.
- **isaac-gchat** bean/isaac-baf1 (fc2f6a8): rebased onto 00fd113, isaac-agent repinned to 372b7de, version 0.2.15. bb spec 180/0, features 56/0 (baf1 scenario green: #index 0 All green., #index 1 Looking now.). The only .feature diff is dropping @wip. Gate PASS at 22f3ae4 over 00fd113.
- bb lint: main is red already (74 errors, all clj-kondo not resolving speclj `should-*`). baf1 adds 2 more of the same kind in guidance_spec. Needs a kondo speclj config; out of scope here.

Blocking: gchat main moved to 5a2a2a4 (`plan: isaac-vlxz — attachment message is request #1`). That commit edits the vlxz scenario in outbound.feature, which is part of baf1's baselined blob 8d57019. Gate on the squash (621d6a6 over 5a2a2a4):

    isaac-baf1 bean-gate: FAIL (1) — isaac-gchat @ HEAD 621d6a6
      FAIL isaac-gchat features/comm/gchat/outbound.feature: baselined block "Scenario: comm__send with an attachment uploads it, then posts the message referencing it (isaac-vlxz)" (baseline line 520) was changed
          + | #index                                           | 1                     |

That is the planner's own vlxz edit, not the worker's, so I can't revert it. Squash reset, nothing pushed to gchat main. Needs: re-baseline baf1 on gchat 5a2a2a4. The squash then lands as is.

feature-baseline: isaac-gchat 5a2a2a4b70d351043595772d994ec1cef797cd04
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 292e54a6a85070f2973bd2418789faa4445cc135 490



## Planner adjustment (2026-09-25, prowl@isaac-plan) — re-baseline on gchat 5a2a2a4

Gate FAIL was the planner's own isaac-vlxz edit (`#index` 1 on the attachment scenario), which sits inside the previous baf1 blob. The worker did not change that block. Do not revert it.

**Re-baselined** on isaac-gchat main `5a2a2a4` (newest lines in force):

    feature-baseline: isaac-gchat 5a2a2a4b70d351043595772d994ec1cef797cd04
    feature-blob: isaac-gchat features/comm/gchat/outbound.feature 292e54a6a85070f2973bd2418789faa4445cc135 490

baf1 scenario order is unchanged: `#index` 0 = All green., `#index` 1 = Looking now.

`bb lint` speclj `should-*` unresolved (74 on main, +2 on this bean) is ambient. Do not absorb a kondo config here. Do not fail this bean on it.

### Worker now

1. Rebase `bean/isaac-baf1` onto origin/main `5a2a2a4` if not already (`fc2f6a8` was ready). Keep implementation. Worker `.feature` diff may only drop `@wip`.
2. `bb bean-gate verify isaac-baf1` exit 0.
3. Squash-land gchat. Agent pin `372b7de` is already on main. Record `main-sha: isaac-gchat <sha>` and `main-sha: isaac-agent 372b7debf6f8fe5167581d873e04c31818f5b6c3`.
4. Do not revert the vlxz `#index` row. Do not recut queue-first.

This note resets the verify-fail counter.

## Conflict (scrapper@isaac-work-2, 2026-09-25) — gate FAIL on the bean's own contract history

Rebased bean/isaac-baf1 onto isaac-gchat main 5a2a2a4 cleanly (now 558ec78, pushed). Worker .feature diff = only the baf1 `@wip` line removed. bb spec 180/0, bb features 56/0. The feature blob check now passes. The gate still exits 1:

    isaac-baf1 bean-gate: FAIL (2) — isaac-gchat @ HEAD 558ec78 (branch bean/isaac-baf1)
      FAIL contract line removed or edited in 5a9a5ad:   Chat, in that order.
      FAIL contract line removed or edited in 5a9a5ad:   answer — both post (isaac-baf1)": the tool's delivery and the reply both reach

Cause: planner commit 5a9a5ad8 (Micah, "re-baselined after worker conflict") reworded the first `## Acceptance` checkbox after the bean was already gated. contract-failures (src/isaac/bean_gate/core.clj:88) checks every consecutive pair of versions from the first gated one. That makes the edit a permanent FAIL: appending the old lines back does not clear it, and `## Exceptions` does not cover acceptance-line edits. Workers may not touch contract lines. Not landed; bean stays in-progress.

Needs a planner/human decision, e.g. a gate mechanism for authorized acceptance edits, or re-cutting the bean with the new acceptance text.


## Held (awaiting human, 2026-09-25)

Escalated to human by **prowl**@isaac-plan. Blocking: gate FAIL is permanent — Micah's `5a9a5ad8` reworded the first `## Acceptance` checkbox after the bean was gated, and `contract-failures` has no exception path for that edit.

Product is done and green. Do not re-dispatch a re-baseline; it cannot clear this. Resumes only on explicit human action.

### What is done (not landed)

- isaac-agent squash-landed `372b7de` (impl-id, tick keeps live comms).
- isaac-gchat `bean/isaac-baf1` @ `558ec78` on main `5a2a2a4`. Worker `.feature` diff is only the dropped `@wip`. `bb spec` 180/0, `bb features` 56/0. Feature-blob check passes.
- Scenario order stands: `#index` 0 = All green. (reply at turn end), `#index` 1 = Looking now. (queued send at the delivery tick). Do not recut queue-first. Do not restore `gchat__send` or the mw27 guard.

### Why the gate cannot pass

`contract-failures` (`src/isaac/bean_gate/core.clj`) walks every consecutive pair from the first gated version. A removed or edited non-blank line under `## Acceptance` fails forever. `## Exceptions` does not cover acceptance-line edits. Appending the old lines back does not clear the pairwise miss. Workers may not touch contract lines. Re-baselining only appends `feature-*` lines; it does not rewrite history.

The edited lines (commit `5a9a5ad8`, Micah, "re-baselined after worker conflict"):

- removed: `answer — both post (isaac-baf1)": the tool's delivery and the reply both reach`
- removed: `Chat, in that order.`
- replaced with the queue-first wording now in `## Acceptance` (request 0 = response, request 1 = queued send).

That reword matches the scenario that is already green. The gate is rejecting the planner/human's own correction, not a worker drift.

### Human chooses one

1. **Gate change (preferred if this will happen again).** An authorized acceptance edit — planner or human commit, not a worker/verifier trailer — must not be a permanent FAIL. Until that ships, this bean cannot land through the gate.
2. **Re-cut.** New bean whose Acceptance is written with the queue-first wording *before* the first baseline, scenarios moved or re-`@wip`'d onto it, this bean closed as superseded (not completed through the gate). Do not ask a worker to edit `5a9a5ad8` or the Acceptance section.

`bb lint` speclj `should-*` unresolved is ambient. Do not absorb it here.

No crew re-picks this until a human re-hails plan/work or re-promotes.



## Planner decision (2026-09-25 05:45Z) — gate fixed, re-run it

Micah chose the gate change (isaac-3rbl, on isaac main 3c8d9c9): the append-only window now starts at the newest planner baseline, so the 5a9a5ad8 edit no longer counts. Verified locally: `bb bean-gate verify isaac-baf1` → PASS against bean/isaac-baf1 @ 558ec78. Pull the isaac clone, re-run the gate, land the gchat half (agent part is already main 372b7de = 0.1.84; pin it).
