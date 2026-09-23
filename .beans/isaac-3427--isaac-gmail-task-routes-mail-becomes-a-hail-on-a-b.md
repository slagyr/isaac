---
# isaac-3427
title: 'isaac-gmail: task routes — mail becomes a hail on a band, with the message as payload'
status: completed
type: feature
priority: normal
created_at: 2026-09-23T19:29:04Z
updated_at: 2026-09-23T22:39:45Z
blocked_by:
    - isaac-sb6d
---

Bean 3 of the mail triage set (after routes isaac-sb6d). Some mail should trigger work rather than a reply. Work in Isaac is dispatched by hail, so a task route sends a hail instead of starting a thread session.

## Design

- Route action `:task` with `:band` (required) and optional `:params` template map. On match: label `isaac/<route>` first (idempotency, from isaac-sb6d), then send a hail `{:frequencies {:band <band>} :params (merge params {:gmail/id :gmail/thread-id :from :subject :body-excerpt})}` with the mail as data. Optional `:ack true` replies on the thread with a one-line "Got it — filed as <band>" (default false; no reply otherwise).
- Hail is a separate module. Resolve `isaac.hail.queue/send!` at runtime (`requiring-resolve`); if the hail module is not installed, log warn `:gmail.route/hail-unavailable`, apply label `isaac/<route>/unsent`, no turn. Do not add a hard dep.
- Security: a `:task` route only fires for senders the gate admitted AND, when the allow-from entry is a `*@domain` pattern, only when `gate/authenticated?` passed (already enforced by drop-reason — assert it in a scenario, since mail is where prompt injection into a work lane would bite).
- Body excerpt capped (config `gmail/task-body-cap`, default 4000 chars); the full message stays readable through the gmail__read tool by id.

## Acceptance (features/comm/gmail/routes.feature, task section)

- [ ] `:task` route with band `ops-inbox` → one hail record with that band and params carrying id, thread id, from, subject, excerpt; label applied before the hail; no session/turn created.
- [ ] Same message seen again (already labelled) → no second hail.
- [ ] `:ack true` → one reply on the thread; default → none.
- [ ] Hail module absent → warn log, `unsent` label, no exception.
- [ ] Excerpt capped at `gmail/task-body-cap`.
- [ ] Manifest, version bump, `bb spec`/`bb features`/`bb lint` green.

Likely repo scope: isaac-gmail (`routes.clj`, `handler.clj`, manifest, features). Read-only in isaac-hail (`queue.clj`, `prepare.clj`).

feature-baseline: isaac-gmail 63f87c6c160537f1761c8b6179b08308606e68f7
feature-blob: isaac-gmail features/comm/gmail/tasks.feature b910643109113610be22844f24d1ae4e5e7793d2


## Summary of Changes

A route with :action :task now sends the gated message as a hail instead of
starting a thread session. isaac.comm.gmail.handler/dispatch-decision! grew a
:task branch that runs after the verdict label is applied (same idempotency
check as any other route, inherited unchanged from isaac-sb6d), calling new
isaac.comm.gmail.tasks/dispatch!.

isaac.comm.gmail.tasks builds a hail record — {:frequencies {:band <route's
:band>} :params (mail fields merged over the route's own :params, mail
fields winning a key collision)} — with :params carrying :gmail/id,
:gmail/thread-id, :from, :subject, :body-excerpt (capped at
gmail/task-body-cap, default 4000; the full message stays readable through
gmail__read by id). isaac.hail.queue/send! is resolved at runtime with
requiring-resolve since isaac-hail is not a dependency of isaac-gmail; when
it can't be resolved, tasks.clj logs :warn :gmail.route/hail-unavailable and
applies label isaac/<route>/unsent (reusing labels/apply-label!, which
concatenates the label prefix with the whole "<route>/unsent" string) —
never an exception, never a turn. When the route sets :ack true, one reply
("Got it — filed as <route>: <subject>.") posts on the thread after a
successful hail send; the default (no :ack) sends none.

isaac.comm.gmail.routes: known-actions now includes :task. decide's matched
branch conditionally carries :band, :ack, and :params forward on the
decision map (only when the route sets them, so :converse/:ignore decisions
are unchanged — verified by existing spec equality assertions). check-config
now also requires :match :from on a :task route (same as :converse) and
requires :band on a :task route, each naming the offending route.

Manifest: gmail-routes' :band/:ack/:params schema entries are no longer
"reserved for isaac-3427" placeholders — they're live, with :params
documented as merging under the mail payload. New config key
gmail/task-body-cap (:int, default 4000). Version 0.2.0 → 0.2.1.

feature-steps/isaac/gmail_steps.clj: three new steps — "Given the hail
module is installed" (interns isaac.hail.queue/send! directly, not via
require, so after-scenario can remove-ns it and requiring-resolve goes back
to failing for the next scenario, matching a real absent-module host), "Then
one hail was sent to band \"<band>\" with:" (path|value table against the
matching record's :params), and "Then no hail was sent". Also added
regex-cell support (`#"..."` DOTALL, per TABLES.md's shared Cell Syntax) to
the existing "the sent mail decodes to:" step's "text" (and header)
comparison, needed by the ack scenario's partial-match assertion — literal
cells still compare by exact equality, so gmail.feature's existing literal
uses are unaffected. push-watch now resets :outbound-http-requests before
each push (mirroring the existing grover/clear-provider-requests! reset) so
a scenario with two pushes (the ack-toggle scenario) asserts each push's own
outbound requests rather than the whole scenario's cumulative history — no
other scenario pushes twice, so this is scoped to a new pattern, not a
behavior change for existing ones.

New: src/isaac/comm/gmail/tasks.clj + spec/isaac/comm/gmail/tasks_spec.clj.
Extended: routes.clj + spec, handler.clj, isaac-manifest.edn,
feature-steps/isaac/gmail_steps.clj.

6/6 tasks.feature scenarios green, @wip removed. Full suite: 100 spec
examples green, 30 feature examples green (6 tasks + 11 routes + 6 gmail + 2
tenants + 5 watch — all previously-passing scenarios unchanged), bb lint
src/ clean. pull.feature/triage.feature @wip scenarios (bean u80t/betb)
untouched.

## Landed on main (2026-09-23)

main-sha: isaac-gmail dd02543609ab2783251d2fe9b3881f058ec4c99a
