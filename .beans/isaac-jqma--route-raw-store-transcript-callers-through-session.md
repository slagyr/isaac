---
# isaac-jqma
title: Route raw-store transcript callers through SessionPolicy; torn-line repair moves behind the store SPI
status: in-progress
type: task
priority: high
created_at: 2026-09-10T15:03:58Z
updated_at: 2026-09-10T15:03:58Z
parent: isaac-mmod
---

Repo: isaac-agent. After isaac-mmod five callers still hold the primitive SessionStore (SPI) for transcript work: bridge/resume (dangling-tool-call repair + torn-line truncation against the chronicle file path), bridge/status (turn count), charge/transcript, api/create-session!. Today the episodes policy keeps the backing transcript under the stable session id, so these read the right data; they go wrong the moment isaac-b6w0 moves the episode transcript into a per-episode directory. Fix now so b6w0 changes the mapping in one place.

## Required
1. Store SPI: sidecar + memory implement repair-transcript! (torn trailing EDNL line truncated to the last complete line; memory store also replaces its in-memory copy). Shared helper in impl-common.
2. Policies: chronicle + episodes repair-transcript! delegate to the store.
3. bridge/resume: resolve the session's crew policy (policy/for-crew from the session record) and use policy repair-transcript!, get-transcript, append-message!. Turn-marker clearing stays on the primitive on purpose (the policy's clear seals an open episode; a resumed hail must not seal).
4. bridge/status: turn count via the crew policy's get-transcript.
5. charge/transcript: policy/for-request.
6. api/create-session!: open through the crew policy (chronicle when no crew/config), preserving today's delegation spec.

## Scenarios (@wip → promoted on green)
- features/session/resume_repair.feature: torn-line repair on a crew with a recording session policy proves resume calls repair-transcript! and get-transcript on the policy, and the transcript is repaired.
- features/bridge/commands.feature: /status on a recording-policy crew counts turns through the policy.
- spec: api create-session! routes through the crew policy; store repair-transcript! truncates a torn line (sidecar + memory).

## Acceptance
bb spec && bb features green in isaac-agent; grep -rn 'store/(get-transcript|active-transcript|append-message!|open-session!)' src outside src/isaac/session and src/isaac/episodes and src/isaac/recall is empty.
