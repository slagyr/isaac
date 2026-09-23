---
# isaac-3427
title: 'isaac-gmail: task routes — mail becomes a hail on a band, with the message as payload'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-23T19:29:04Z
updated_at: 2026-09-23T22:25:30Z
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
