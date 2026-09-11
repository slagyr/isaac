---
# isaac-exg7
title: 'Post-deploy: confirm parallel tool-call batching on real zanebot beans (isaac-la8h rollout)'
status: completed
type: task
priority: normal
created_at: 2026-07-09T16:37:56Z
updated_at: 2026-09-04T22:38:32Z
---

## Goal

One-time operational validation of isaac-la8h after it deploys to zanebot.

isaac-la8h (invite parallel tool calls) is verified on code + tests. This bean
carries its post-deploy observation, which is impossible to satisfy before the
code ships.

## Steps

- After isaac-la8h is deployed to zanebot, re-run the tool-call batch-size
  distribution analysis over a real composer (or gpt) bean transcript.
- Expected: >1-call assistant responses now occur (vs the pre-change baseline of
  6,561/6,561 single-call responses).
- If the composer still refuses to batch, RECORD that finding here — that is a
  model-habit result, not a harness defect, and closes this bean either way.

## Acceptance

- [x] Batch-size analysis re-run on a real post-deploy bean transcript.
- [x] Result recorded: either >1-call responses observed, or a documented
      finding that the model declines to batch despite the invitation.

## Origin

Split from isaac-la8h Acceptance (2026-07-09, prowl) per verifier escalation on
thread 16e9b844. The implementation/test contract is complete and verified;
this operational rollout check is inherently post-merge.


## Result (2026-09-04, plan@isaac-plan) — batching confirmed

Method: per-cycle `:chat/stream-response :tool-calls-count` events across
every zanebot `server*.log` (31 files, 30-day retention → 2026-08-04 onward;
no pre-la8h logs survive, so the baseline is la8h's own 6,561/6,561
single-call analysis of 2026-07-08). Transcript rows are NOT usable for this:
the drive persists each tool call as its own assistant row (l7lv), so a batch
is indistinguishable from serial calls there. `:turn/model-response-summary
:tool-calls-count` is also unusable — it samples only a turn's LAST response.

Tool-bearing responses (count > 0) with more than one call:

| period | provider/model | responses | >1 call |
|---|---|---|---|
| Aug 2026 | chatgpt / gpt-5.4 | 5,094 | 43.5% |
| Aug 2026 | grok-4.6 | 15,834 | 62.6% |
| Aug 2026 | grok-4.5 | 5,272 | 31.1% |
| Aug 2026 | gpt-5.4-mini (gist) | 564 | 3.7% |
| 2026-09-03 | gpt-5.4 | 463 | 53.8% |
| 2026-09-03 | grok-4.6 | 826 | 47.7% |
| 2026-09-04 | gpt-5.4 | 570 | 53.9% |
| 2026-09-04 | grok-4.6 | 708 | 56.2% |

Batch sizes: 2–4 dominate; 5+ calls in ~10–18% of grok-4.6 tool responses.
The July baseline (0% >1-call) is gone on both providers; the invitation
works. gpt-5.4-mini (episodes gist worker) essentially never batches —
expected, single-purpose prompts.

Note on isaac-j2v0 (parallel EXECUTION, landed 2026-09-04): this analysis
measures what the models emit, not wall clock saved; a follow-up could
compare per-cycle tool wall time before/after the j2v0 pin.

Acceptance:
- [x] Batch-size analysis re-run on real post-deploy transcripts (log-derived).
- [x] Result recorded: >1-call responses observed at ~45–60% on gpt-5.4 and
      grok-4.6.

## Landed on main (2026-09-04)
main-sha: isaac 8e895d8c60026e53037a24a2fcfceb93714dc775

## Verification (2026-09-04, perceptor@isaac-verify)

Operational observation bean. No product-code landing required; the required record is already on isaac origin/main.

Evidence:
- Result section (commit 8e895d8c) re-ran batch-size analysis from zanebot `server*.log` `:chat/stream-response :tool-calls-count` events (31 files, 2026-08-04 onward). Transcript rows are documented as unusable because drive persistence (l7lv) splits a batch into one assistant row per tool call.
- Observed >1-call tool-bearing responses vs the 2026-07-08 6,561/6,561 single-call baseline, including 2026-09-04 gpt-5.4 53.9% (570 responses) and grok-4.6 56.2% (708 responses); Aug gpt-5.4 43.5% and grok-4.6 62.6%.
- Acceptance checkboxes in the Result section are checked. This is a model-habit confirmation, not a harness defect.
