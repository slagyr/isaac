---
# isaac-betb
title: 'isaac-gmail: triage fallback — a cheap model turn names the route for mail no rule claims'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-23T19:29:05Z
updated_at: 2026-09-23T22:40:14Z
blocked_by:
    - isaac-sb6d
---

Bean 4 of the mail triage set (after routes isaac-sb6d; task routes isaac-3427 optional). Mail that no rule claims is `:unrouted` today (labelled, no turn). This bean lets a small model decide instead, records the verdict as a label so every disagreement becomes a new rule, and defaults to ignore until Micah has looked at a week of verdicts.

## Design

- Config `gmail/triage` `{:model <alias> :crew <crew> :choices ["team" "newsletters" "ignore"] :default "ignore" :apply false}`. When present, an `:unrouted` message runs ONE turn in a dedicated session `gmail-triage` (`:context-mode :reset`, no tools) with a fixed system prompt: the route names + one-line descriptions from config (`:desc` on each route) and the message's from/to/subject/first 2000 chars; the only valid output is one route name or `ignore`. Non-matching output → `:default`.
- Verdict label `isaac/triage/<verdict>` always. With `:apply false` (default) that is all — the message is still not routed, so Micah can audit before trusting it. With `:apply true` the message is then dispatched as if that route had matched (converse/ignore/task).
- Token discipline: reset context, no tools, capped excerpt, one cycle max (`:max-cycles 1`). Bulk mail never reaches triage because the gate signals from isaac-sb6d ignore it first.
- Log one info line per verdict: id, from, subject, verdict, applied?.

## Acceptance (features/comm/gmail/triage.feature)

- [ ] Unrouted message + triage configured → exactly one turn in session `gmail-triage` with the configured model; label `isaac/triage/team` from the echo response "team"; `:apply false` → no further turn.
- [ ] `:apply true` + verdict "team" (a converse route) → thread session turn starts on that route's crew, label `isaac/team` also applied.
- [ ] Verdict not in `:choices` → `:default` used, label `isaac/triage/ignore`.
- [ ] Triage session's transcript does not grow across two messages (reset).
- [ ] No `gmail/triage` config → unrouted behaviour from isaac-sb6d unchanged.
- [ ] Manifest, version bump, `bb spec`/`bb features`/`bb lint` green.

Likely repo scope: isaac-gmail (`routes.clj`, new `triage.clj`, `handler.clj`, manifest, features).

feature-baseline: isaac-gmail 63f87c6c160537f1761c8b6179b08308606e68f7
feature-blob: isaac-gmail features/comm/gmail/triage.feature 527cb268f8eebc9c52fd6d49b876548901a5e2bb
