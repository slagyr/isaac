---
# isaac-3wic
title: Configurable hail id naming strategy
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-26T03:33:53Z
updated_at: 2026-06-26T16:22:23Z
blocked_by:
    - isaac-hoaq
---

Make hail id minting strategy configurable via Isaac config, mirroring `:sessions.naming-strategy`. The current `isaac-hoaq` behavior hardcodes bare short-uuid ids; this bean adds config dispatch so installs can choose default short-uuid, full UUID, or deterministic sequential `hail-N`.

## Decisions locked (2026-06-25, Micah)

- Config path: `hail-settings.naming-strategy`
- Allowed values: `short-uuid`, `uuid`, `sequential`
- Default when absent: bare short-uuid
- No legacy path aliases; clean cutover to the new config key
- Router reach-`:all` child deliveries use the same configured hail id strategy
- Session spawn ids remain out of scope

## Feature file

Committed `@wip` scenarios live in:

- `isaac-hail/features/hail-naming.feature:9`
- `isaac-hail/features/hail-naming.feature:27`
- `isaac-hail/features/hail-naming.feature:40`
- `isaac-hail/features/hail-naming.feature:82`

These scenarios lock:

1. sequential strategy resumes at `hail-2` when `hail-1` already exists outside pending
2. `uuid` strategy emits a full UUID and keeps `thread-id == id`
3. sequential strategy propagates to router reach-`:all` child deliveries (`hail-2`, `hail-3`)
4. absent config still defaults to a bare short-uuid

## Implementation notes

- The mint seam is `src/isaac/hail/queue.clj` (`next-id` / naming-strategy dispatch).
- `send!` and router child fan-out must both read the configured strategy.
- Sequential mode must restore the old counter-sync behavior so existing `hail-N` records are respected.
- Default short-uuid behavior already exists in `main`; this bean adds override behavior without changing the default.

## Acceptance

Run in `isaac-hail`:

```bash
cd /Users/micahmartin/agents/plan/isaac-hail
bb features features/hail-naming.feature
bb spec spec/isaac/hail/queue_spec.clj
```

Targeted feature selectors:

```bash
cd /Users/micahmartin/agents/plan/isaac-hail
bb features \
  features/hail-naming.feature:9 \
  features/hail-naming.feature:27 \
  features/hail-naming.feature:40 \
  features/hail-naming.feature:82
```

Definition of done:

- remove `@wip` from `features/hail-naming.feature`
- `hail-settings.naming-strategy` accepts `short-uuid`, `uuid`, and `sequential`
- unset config still yields bare short-uuid ids
- sequential mode resumes after existing `hail-N` files and applies to reach-`:all` children
- `bb spec` and `bb features` are green in `isaac-hail`

## Verification
Verified against fetched GitHub `isaac-hail` head `b5f3db2` (feature commit `41a3cfd` plus follow-up dep bump). The core queue logic checks out and `bb spec spec/isaac/hail/queue_spec.clj` passed (`8 examples, 0 failures, 22 assertions`). The feature scenarios are present and no longer `@wip`. `bb features features/hail-naming.feature` still trips an ambiguous `Given config:` step match in the current verifier path because the alias loads both `isaac.session.session-steps` and `isaac.server.server-steps`, but per Micah's close-out guidance this is being treated as verifier/review-path noise rather than a worker miss. Closing the bean on the delivered implementation.
