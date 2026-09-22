---
# isaac-mu1i
title: 'Live smoke before a module ships: real scheduler, real Google (test project), no stubs'
status: in-progress
type: task
priority: high
tags:
    - google
    - process
created_at: 2026-09-19T23:48:52Z
updated_at: 2026-09-22T22:10:07Z
parent: isaac-bv1l
---

2026-09-19: the first live day for the Google modules on yopp surfaced SIX defects that every green suite missed — tick! NPE (door-up? shadowed; scheduler calls (tick! {})), create returns an Operation, list needs Google's filter, the inbox worker was never scheduled, Chat senders have no email, and an earlier one where the OIDC verifier's reflective key construction did not exist under bb. Common cause: harnesses drive timers by step and stub Google's API from docs, so neither the server's own scheduling nor Google's actual contract was exercised.

Do: a repeatable smoke on a live host before a Google-module release — start the real server (systemd unit or `isaac server`), let the scheduler run the registration tick and the inbox worker unassisted, and drive one real event through Google against a test project/space: login → registration → outbound send → inbound push → turn → reply. Record it as a checklist in isaac-google/doc/rollout.md (host-agnostic) and gate module version bumps on it. Also: unit specs that call components the way production does (e.g. (tick! {}) with no opts) — cheap and would have caught two of the six.
