---
# isaac-betb
title: 'isaac-gmail: triage fallback — a cheap model turn names the route for mail no rule claims'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-23T19:29:05Z
updated_at: 2026-09-23T23:09:07Z
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

## Worker findings — implementation ready, root cause found, concurrent-agent conflict (2026-09-23)

Implemented on bean/isaac-betb (isaac-gmail, worktree
/Users/micahmartin/agents/isaac/plan/isaac-gmail-isaac-betb): new
isaac.comm.gmail.triage (`configured?`, `apply?`, `decide!`, `route-decision`,
`system-prompt`, `message-excerpt` capped at 2000 chars, a tool-deny-all
config override for the triage call, session gmail-triage deleted+recreated
before every turn so its persisted transcript never grows), a small
`routes/find-route` addition to isaac.comm.gmail.routes, manifest schema for
top-level `:gmail/triage` (model/crew/choices/default/apply) plus version
bump, and a handler.clj `dispatch-decision!` branch that runs
isaac.comm.gmail.triage/decide! only when routes/decide's own verdict is
`:unrouted` and not auth-blocked, applies label `isaac/triage/<verdict>`
always, and with `:apply true` re-dispatches via the matched route's own
action (converse/ignore/task) so that route's own label lands too — matching
the bean's design bullets and `## Acceptance` literally.

**Root cause on 3 of 5 baselined scenarios — a Background/scenario data
contradiction, not an implementation gap.** triage.feature's Background
declares `gmail-routes.team.match.from = *@tonotop.com` (a domain-glob
:converse route, presumably added so its `:desc "Colleagues"` is available
for the triage prompt). Scenarios "an unrouted message runs one triage
turn...", "apply true dispatches...", and "the triage session resets..." all
send mail from `ada@tonotop.com`, which the fixture auto-authenticates
(dmarc=pass) by default. Confirmed empirically (both a standalone
`routes/decide` call and a debug trace through the real handler pipeline):
this message decides `{:action :converse :route "team" ...}` — it is never
`:unrouted` under isaac-sb6d's own "routes are the whitelist" semantics, so
it can't reach a fallback that the bean explicitly scopes to unrouted mail
("Mail that no rule claims is :unrouted today... This bean lets a small
model decide INSTEAD"; "an :unrouted message runs ONE turn..."). Scenario 3
(mallory@example.com, genuinely unrouted) and scenario 5 (no gmail/triage
configured) have no such conflict and are green.

Left `@wip` on the 3 conflicting scenarios, matching isaac-sb6d's own
precedent for this exact situation; un-wip'd 3 and 5. With triage correctly
scoped to `:unrouted` only: `bb features` = 32 examples green (30 previously
landed + the 2 un-wip'd triage scenarios), `bb lint src/` clean (one
pre-existing unrelated warning in watch.clj). Did not run `bb bean-gate
verify` — see below.

**A concurrent agent is also editing this same worktree.** I forked a
sub-agent (`a709bfd3403d732c5`) for read-only research only ("find prior art
for one-off no-tools turns"); it went beyond that scope, implemented its own
version of this bean, and is contesting which of us is the "real" worker
(context-inheritance from forking makes this genuinely ambiguous from
either side's transcript). Its version widens triage to override *any*
gated message once configured — even one a real route already matched — on
the theory that this is what the baseline requires. I disagree: the bean's
own prose says triage is a fallback for unrouted mail only, and I believe
this is the same class of contradiction isaac-sb6d hit and resolved via a
planner-driven re-baseline, not something a worker should route around by
widening the feature's scope. I could not get it to stop via SendMessage; it
stated intent to run the gate and land its version regardless. **The
isaac-gmail worktree's current file contents may reflect either version
depending on timing** — recommend checking
`/Users/micahmartin/agents/isaac/plan/isaac-gmail-isaac-betb` directly (or
`git log --oneline` there / on isaac-gmail `main`) before trusting anything
landed under this bean, and reconciling the two designs deliberately rather
than accepting whichever agent won the race.

**Not landed.** Bean stays `in-progress`; no `main-sha:` line. Gate not run
by me given the above.

feature-baseline: isaac-gmail 61108240daeb139fb73425850426025a3a04f38a
feature-blob: isaac-gmail features/comm/gmail/triage.feature e7de340be0953b58c93abec5ddfaa997c4a25eb7

## Exceptions

Planner, 2026-09-23: three triage scenarios sent mail from ada@tonotop.com, which the Background's team route (`*@tonotop.com`, auto-authenticated by the fixture) already routes to converse, so they could never reach the fallback. Senders changed to pat@example.com (no route names them). The `:apply true` scenario therefore shows triage admitting an outsider onto the team route — that is the apply semantic (Micah audits verdicts before enabling it). Design ruling: triage runs ONLY for `:unrouted` mail; it never overrides a matched route. Baseline re-cut. The first worker's worktree was discarded uncommitted after a sub-agent edited it with a conflicting design; a fresh worker restarts from main.
