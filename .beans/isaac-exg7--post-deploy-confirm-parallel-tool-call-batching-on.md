---
# isaac-exg7
title: 'Post-deploy: confirm parallel tool-call batching on real zanebot beans (isaac-la8h rollout)'
status: todo
type: task
priority: normal
created_at: 2026-07-09T16:37:56Z
updated_at: 2026-07-09T16:37:56Z
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

- [ ] Batch-size analysis re-run on a real post-deploy bean transcript.
- [ ] Result recorded: either >1-call responses observed, or a documented
      finding that the model declines to batch despite the invitation.

## Origin

Split from isaac-la8h Acceptance (2026-07-09, prowl) per verifier escalation on
thread 16e9b844. The implementation/test contract is complete and verified;
this operational rollout check is inherently post-merge.
