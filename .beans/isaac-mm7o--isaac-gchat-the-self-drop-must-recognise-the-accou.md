---
# isaac-mm7o
title: 'isaac-gchat: the self-drop must recognise the account by users/<id>, not only by email'
status: completed
type: bug
priority: normal
tags:
    - google
    - comm
created_at: 2026-09-19T23:48:52Z
updated_at: 2026-09-22T22:39:32Z
parent: isaac-bv1l
---

2026-09-19 23:47Z on yopp: after Isaac replied in the thread, Google pushed the reply back as an event; the gate dropped it — but as :sender (yopp's Chat user id users/101936183306307394083 is not in allow-from), not as :self, because gchat/account is an email and the sender carries none. Right outcome by luck: an operator who allows domain:<id> would let Isaac's own replies through and create an echo loop.

Do: learn the account's users/<id> (spaces.members or people/me on first use; cache in state) and check :self against it; scenario: Isaac's own reply, with a domain allow-list, drops :self.

## Handoff (worker, 2026-09-22)

The prior commit (unpushed, left by a 2026-09-20 worker, rebased onto main by
the time I picked this up) already had the whole shape: `isaac.comm.gchat.self`
caches Isaac's own `users/<id>` in an atom (not config), `:gchat/account-id` in
config short-circuits it, the gate matches sender `:user` against it (drop
`:self`), the manifest declares the key in `:extra-schema` (undeclared config
keys get pruned to nil before the gate sees them), and the mm7o scenario in
`features/comm/gchat/inbound.feature` was present and green.

Design note: instead of calling `spaces.members`/`people/me`, it learns the id
opportunistically from the response of Isaac's own outbound `create-message!`
calls (`isaac.comm.gchat/post-chunks!` now wraps the call in
`self/learn-from-send!`). That's sound for the bug as reported — the send that
triggers Chat's echo-back always happens before the pushback event arrives — so
I kept it rather than widening scope to add a proactive lookup call.

What I changed: `src/isaac/comm/gchat/self.clj` had `[clojure.string :as str]`
required but never used (`bb lint` flagged it) — removed the require, kept the
docstring. No other production/test changes; scope stayed to the self-drop.

Verified: `bb lint` clean on the touched files; `bb spec` → 91 examples, 0
failures, 164 assertions; `bb features` → 28 examples, 0 failures, 64
assertions; `bb ci` exit 0, same counts. (Full unscoped `bb lint` still flags
51 pre-existing errors in `spec/isaac/comm/gchat_spec.clj`, a file this bean
never touches — untouched by this diff, left alone.)

Squashed to one commit on `bean/isaac-mm7o` (`d723e6a`, amended over the prior
worker's commit, same message) and pushed with `--force-with-lease` (remote
held the pre-rebase `eb1b621`, as expected).

isaac-google worktree: not touched — the id is learned entirely in
isaac-gchat's own send/gate/self layer; no isaac-google client-layer change
was needed.

## Planner check (2026-09-22)

Reran on bean/isaac-mm7o d723e6a: `bb spec` 91/0, `bb features` 28/0. Diff reviewed. PR opened to isaac-gchat main; tagged `unverified`. Open question for a later bean, not this one: the learned id is one per process; with several tenants (isaac-1zkz) it should be keyed by tenant. isaac-xy2i is editing gchat on a sibling branch and will need to rebase over this.

## Requirement added (2026-09-22, Micah): the learned id is per tenant

The account users/<id> cache must be keyed by the Google organization (tenant, isaac-1zkz), not one id per process. Each comm speaks for one organization (`:gchat/google`), so `learn-from-send!` records the id under that tenant and the gate checks self against that tenant's id. `:gchat/account-id` stays a per-comm (per-tenant) config short-circuit. Scenario: two tenants, each learns its own id from its own send; tenant A's echo is dropped as :self by A's id and is not mistaken for B's. Same branch, same PR; planner re-verifies.

## Handoff 2 (worker, 2026-09-22)

Added tenant-scoping to the learned-id cache on top of Handoff 1's work, same
worktree, same bean/isaac-mm7o branch, PR #1 untouched.

`isaac.comm.gchat.self` (`src/isaac/comm/gchat/self.clj`): the cache is now a
map keyed by tenant, not a single atom. `learn-from-send!` takes `[tenant
response]`, `account-user` takes `[tenant]`, `resolve-account-user` takes
`[tenant cfg]` (still prefers that comm's own `:gchat/account-id`, falls back
to the learned id for that tenant only — never another tenant's).

Callers now resolve the tenant via `isaac.comm.gchat.tenant/of-comm` (already
used by `access-token` for the same purpose) before touching the cache:
`src/isaac/comm/gchat.clj` (`post-chunks!` gained a `tenant` param, threaded
from both `send!*` and `on-reply*`) and `src/isaac/comm/gchat/handler.clj`
(`handle-event` computes `cfg`/`tenant` once, passes `:account-user
(self/resolve-account-user tenant cfg)`).

`spec/isaac/comm/gchat/self_spec.clj`: rewritten for the new arities plus new
cases — two tenants keep separate ids, a nil-tenant (single-org host) still
learns, `resolve-account-user` never answers with another tenant's id. 10
examples (was 7).

`feature-steps/isaac/gchat_steps.clj`: two small additions so the scenario
could exercise the *learned* path (not just `:gchat/account-id`) at the
feature level — the outbound `/messages` stub now returns a `:sender.name`
derived from the bearer token (`users/self-<token>`), so each organization's
stubbed sends learn a distinct id; and `g/after-scenario` now calls
`isaac.comm.gchat.self/forget!` so the cache (a `defonce` atom, process-wide)
doesn't leak a learned id across scenarios.

New scenario in `features/comm/gchat/inbound.feature`: "two tenants each learn
their own id; one's echo is never mistaken for the other's (isaac-mm7o)". Two
comms (`gchat`/tonotop, `gchat-acme`/acme) each send and learn their own id;
an inbound event carrying acme's id is delivered first while `gchat` speaks
for tonotop (routes, not dropped as self — session count 1, `:gchat/message-routed`),
then again after reconfiguring `gchat` to speak for acme (drops as self —
session count stays 1, `:gchat/message-dropped :self`). I verified this
scenario actually catches the regression: temporarily made the cache
single-key (ignoring tenant) and the "routes, not self" assertion failed
(`Expected: 1, got: 0`) as expected; reverted before finishing.

Verified: `bb spec` → 94 examples, 0 failures, 168 assertions (was 91).
`bb features` → 29 examples, 0 failures, 68 assertions (was 28). `bb ci` exit
0, same counts, run twice for stability. `bb lint` clean on every touched
production file; the touched spec file shows the same pre-existing
`:refer :all` kondo artifact as every other `*_spec.clj` in this repo
(confirmed against several other spec files) — not a regression.

Squashed into the same one commit on `bean/isaac-mm7o` (amended, message
extended to cover the tenant addition), now `4306062` (was `d723e6a`), pushed
with `--force-with-lease`. PR #1 not touched.

## Planner check 2 (2026-09-22)

Reran on bean/isaac-mm7o 4306062 (per-tenant id cache + two-tenant scenario): `bb spec` 94/0, `bb features` 29/0. PR #1 updated; tagged `unverified`. Note for a repo-wide cleanup, not this bean: isaac-gchat feature fixtures already use tonotop.com / users/yopp on main (inbound, outbound, registrations, tenants features; chat_api_spec) — the new scenario follows that convention. The 09-19 placeholder scrub did not reach these fixtures or isaac-google config.clj/tenants.clj/people.clj docstrings.

## Landed on main

main-sha: isaac-gchat 89846dc

Squash-merged 2026-09-22 (Micah: merge the open PRs); bean branch deleted.
