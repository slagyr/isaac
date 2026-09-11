---
# isaac-3c3z
title: 'isaac-imessage: fix JVM feature suite (lifecycle fails + missing step vars) and re-enable in ci'
status: completed
type: bug
priority: normal
created_at: 2026-07-19T18:31:58Z
updated_at: 2026-07-20T00:26:18Z
---

Split from isaac-dt9h (xapx runner-conversion sweep). NOT a runner issue — the JVM feature suite is red on main independent of the runner conversion.

## Gap

`isaac-imessage` JVM feature suite (`bb jvm-features`) is red on current main, reproducing under both native and JVM independent of the dt9h runner conversion:

- 2 lifecycle scenarios FAIL (config hot-reload surface vs monorepo/split pin age).
- Feature files reference missing step vars — e.g. `imessage-chat-db-responds-with-rows` — steps/features out of sync on main.

Because features were already broken on main, dt9h's `bb ci` gates on the SPEC suites only (native 41ex + jvm-spec 50ex, both green). That preserved parity but leaves the feature suite as tracked debt here.

## Work

- Fix the 2 failing lifecycle scenarios (identify whether the config hot-reload surface needs a product fix or a pin bump).
- Reconcile steps/features: implement or remove the missing step vars (`imessage-chat-db-responds-with-rows`, etc.) so the feature suite loads and runs.
- Once green, wire features back into `bb ci`.

## Acceptance

- [ ] `bb jvm-features` (or native) green in isaac-imessage.
- [ ] steps/features in sync — no missing step vars.
- [ ] `bb ci` green with features included.
- [ ] No regression to native `bb spec` (41ex) or `jvm-spec` (50ex) green at dt9h time.

## Provenance

- dt9h @ isaac-imessage `1912db8`: native specs 41ex/0, jvm-spec 50ex/0; jvm-features red on main pre-conversion. See dt9h "Verify fail resume" note.


## Implementation (scrapper@isaac-work-2)

Root causes:
1. Stale gherclj target cache (chat_db/poller) — deleted; not on features/ main.
2. Lifecycle hot-reload failed config-check: features telly local/root leaked sibling agent HEAD resources (check-crew-broad-directories) while pin ee0b062 lacked the fn.
3. Reply/turn_context e2e: session store missing + -with-nexus wiped :sessions; dispatch swallowed errors.

Fixes (isaac-imessage main f5c13af):
- Pins: foundation 43cf46e, agent 44b55e3, server 08b51e9 (aligned with sibling modules / ykuf server).
- telly via git dep (not local/root).
- imessage_steps: ensure-session-store!, nested nexus, direct dispatch-and-enqueue-reply!.
- lifecycle_feature_spec: server-config-applied (with configure fallback).
- bb ci includes jvm-features.

Verified: bb spec 41/0, bb jvm-spec 50/0, bb jvm-features 15/0 30 assertions, bb ci green.
