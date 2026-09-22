---
# isaac-mu1i
title: 'Live smoke before a module ships: real scheduler, real Google (test project), no stubs'
status: completed
type: task
priority: high
tags:
    - process
    - google
created_at: 2026-09-19T23:48:52Z
updated_at: 2026-09-22T22:39:32Z
parent: isaac-bv1l
---

2026-09-19: the first live day for the Google modules on yopp surfaced SIX defects that every green suite missed — tick! NPE (door-up? shadowed; scheduler calls (tick! {})), create returns an Operation, list needs Google's filter, the inbox worker was never scheduled, Chat senders have no email, and an earlier one where the OIDC verifier's reflective key construction did not exist under bb. Common cause: harnesses drive timers by step and stub Google's API from docs, so neither the server's own scheduling nor Google's actual contract was exercised.

Do: a repeatable smoke on a live host before a Google-module release — start the real server (systemd unit or `isaac server`), let the scheduler run the registration tick and the inbox worker unassisted, and drive one real event through Google against a test project/space: login → registration → outbound send → inbound push → turn → reply. Record it as a checklist in isaac-google/doc/rollout.md (host-agnostic) and gate module version bumps on it. Also: unit specs that call components the way production does (e.g. (tick! {}) with no opts) — cheap and would have caught two of the six.

## Handoff (worker, 2026-09-22)

Implemented `isaac google smoke` as a CLI subcommand (not a `bb` task): it ships with the compiled module, so it runs on any host with `isaac.google` installed via `isaac modules install` — no dev checkout or `bb.edn` required, matching `isaac google status`, whose live-state assembly (Workspace Events listing merged with the Gmail watch's persisted expiry) `smoke` reuses via a new shared helper, `tenant-remote-state`.

**What it checks** (each prints `PASS <check> — <evidence>` / `FAIL <check> — <evidence>`; exits non-zero on any FAIL):

- `door` — unauthenticated POST to `/google/pubsub`, expects 401. Proves the route is bound and isaac-http's identity layer answers.
- `registrations` (one line per tenant on a multi-tenant host) — every configured key (Chat space subscription / Gmail watch) has a live remote registration whose expiry is beyond the renew window. Reads the **live** Workspace Events listing, not a stub.
- `inbox` — `inbox/pending` backlog at or below `--inbox-threshold` (default 0).
- `silent` — count of currently-firing `:google/silent` conditions (from the real `isaac.google.health/evaluate`) at or below `--silent-threshold` (default 0).
- `live-push` (only with `--send-live`) — publishes one real message to the tenant's configured Pub/Sub topic and polls until it reaches the inbox (`:pending`/`:done`/`:failed`) or `--timeout-ms` (default 30000) elapses. Requires the token to hold `pubsub.topics.publish` on the topic (not granted by the rollout's default IAM — documented in the doc as an opt-in grant).

**Defect → check mapping** (full table in `isaac-google/doc/rollout.md` "Smoke before shipping"):

| # | Defect | Check |
|---|---|---|
| 1 | tick! NPE (door-up? shadowed; scheduler calls (tick! {})) | `registrations`/`silent` read a **live** tick's output; a spec also calls `(tick! {})` directly, matching the bean's own suggestion |
| 2 | create returns an Operation | `registrations` fails a key whose remote `:expires-at` is missing/unparsed |
| 3 | list needs Google's filter | `registrations` reads the live listing the same way `status` does |
| 4 | inbox worker never scheduled | `inbox` fails on backlog directly |
| 5 | Chat senders have no email | out of isaac-google's boundary ("knows nothing of Chat or Gmail"); `inbox`/`live-push` catch a handler crashing on the unexpected shape via `inbox/failed`, documented as a partial backstop, not a direct check |
| 6 | OIDC verifier's reflective key construction absent under bb | `live-push` — the only check that verifies a genuinely Google-signed token end to end |

**How to run:** `isaac google smoke [--tenant T] [--url URL] [--send-live] [--renew-within H] [--inbox-threshold N] [--silent-threshold N] [--timeout-ms MS]`. Manually verified against fixture state with `bb -e` (mem-fs, no real network) — door correctly FAILs offline (connection refused), registrations/inbox/silent correctly PASS against seeded fixture state.

**Files:**
- `src/isaac/google/smoke.clj` (new) — pure `decide-door`/`decide-registrations`/`decide-inbox`/`decide-silent`/`decide-live-push`, `render-line`, `ok?`.
- `spec/isaac/google/smoke_spec.clj` (new) — 24 examples, fixtures only, no network.
- `src/isaac/google/cli.clj` — `smoke` subcommand: door probe, live Pub/Sub publish, inbox poll (the untestable evidence-gathering half); refactored `tenant-status-lines` to share `tenant-remote-state` with the new command.
- `src/isaac/google/inbox.clj` + `spec/isaac/google/inbox_spec.clj` — added `inbox/status` (`:pending`/`:done`/`:failed`/`:unknown`) so the live-push driver can poll one record.
- `doc/rollout.md` — new "Smoke before shipping" section: when/how to run, per-check meaning, what each FAIL implies, the defect mapping table, and an explicit note on what the tooling *cannot* prove (a real Chat/Gmail round trip, which stays a manual checklist per §4/§5 since isaac-google doesn't own comm sends).

**Test commands + counts:** `bb ci` green — `bb spec`: 171 examples, 0 failures (24 new, in `smoke_spec.clj`); `bb jvm-features`: 28 examples, 0 failures (unchanged — no new `.feature` scenarios added, per scope below); `bb config-bypass-lint`: ok; `bb lint src spec`: 0 new errors (73 pre-existing errors/13 warnings are a repo-wide clj-kondo/speclj macro-resolution gap present before this change, confirmed identical count on `main`).

**What still needs a live host to prove:**
- The `door`, `registrations` (live-listing path), and `live-push` checks all need a real running server + real Google test project; only their pass/fail *decision logic* is spec'd here (explicitly the untestable half per the task).
- No new `.feature` scenario exercises `isaac google smoke` end-to-end in-process, because the `door`/`live-push` checks make real `babashka.http-client`/Google network calls rather than going through the existing in-process fixture seams (`events/request!` is stubbed in other specs, but `door`'s probe is a real socket call to `http.port`, and a feature-level test would need either a real bound server or a new stub seam). This is a real coverage gap worth closing later if `isaac google smoke` grows; today only manual verification (see above) and the six unit-spec'd decision fns back it.
- Defect #5 (Chat sender email) has no direct check in isaac-google by design (module boundary); worth a bean against `isaac-comm-gchat`'s own smoke story if that's wanted.
- `--send-live`'s default IAM (push-subscription service accounts only, not the tenant user) means it will FAIL with a permissions error until an operator grants `pubsub.topics.publish` to the Isaac account on the topic — documented, not yet exercised against a real project.

## Planner check (2026-09-22)

Reran on bean/isaac-mu1i f3e28d8 (amended from 5b20dd9 to scrub two host-specific mentions to placeholders — `yopp` in doc/rollout.md and a `tonotop` example tenant in the doc and spec): `bb spec` 171/0, features 28/0. PR opened to isaac-google main; tagged `unverified`. Pre-existing on main and NOT this bean: src/isaac/google/config.clj (yopp@tonotop.com example), tenants.clj (:tonotop/tonotop-yopp example), people.clj (micah@tonotop.com) — the 09-19 scrub missed them; separate cleanup. Gap for a later bean: no in-process feature scenario for `isaac google smoke` because door/live-push make real network calls; an HTTP stub seam would close it.

## Landed on main

main-sha: isaac-google a7205c3

Squash-merged 2026-09-22 (Micah: merge the open PRs); bean branch deleted.
