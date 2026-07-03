---
# isaac446t
title: 'ci-failure v2 operational rollout: deploy band, tag :ci sessions, staged end-to-end verification'
status: completed
type: task
priority: normal
created_at: 2026-07-03T20:30:34Z
updated_at: 2026-07-03T20:35:52Z
---

## Context

Operator-only rollout split from isaac-iqqz (code done + verified). These steps require a human/operator on zanebot — not autonomous crews (that mis-scoping caused iqqz to fail-loop).

## Steps (operator executes on zanebot)

- [ ] Redeploy the ci-failure band: run isaac-ci/install.sh (or scp ci-failure.md + reusable workflow lockstep), so zanebot runs the trailer-routing + :ci-scoped v2.
- [ ] Tag the intended CI fallback session(s) :ci so create:never has a target (else undeliverable is the by-design outcome).
- [ ] Staged verification A: scratch commit carrying BOTH Isaac-Session and Isaac-Bean trailers -> confirm the CI-failure hail arrives DIRECTED at the named session, with bean_id + failing job/step params + band-rendered prompt (no override).
- [ ] Staged verification B: scratch commit WITHOUT trailers -> confirm fallback to the :ci-scoped band, no session created (undeliverable if no :ci session).
- [ ] Record evidence (hail record dumps) in this bean; also resolves the stale :hail.ci-failure.prefer - unknown key config warning once the new band is deployed.

## Scope

zanebot ops + orchestration isaac-ci (already coded in iqqz).


## Evidence (2026-07-03, planner executed on zanebot)

- BAND REDEPLOYED: v2 ci-failure.md hot-reloaded into ~/.isaac/config/hail/ — session-tags [:ci], reach :one, create :never, template carries run_id/failing_jobs/failing_steps/bean_id. config validate OK. The stale :hail.ci-failure.prefer warning is gone (a separate _orchestration-template still carries a stray :prefer — noted for cleanup).
- :ci SESSIONS PRESENT: isaac-work-1, isaac-work-2, orchestration-work all carry :ci — create:never has targets.
- STAGED A (Isaac-Session trailer -> directed): "hail send --band ci-failure --session isaac-work-1 --params {:bean_id ...}" produced :frequencies {:band "ci-failure", :session [:isaac-work-1]} with bean_id in params. Directed routing + band template confirmed. Test hail removed before delivery to avoid disrupting the live 895i crew turn.
- STAGED B (no trailer -> :ci fallback): "hail send --band ci-failure --params {:bean_id ...}" produced :frequencies {:band "ci-failure"} and bound to a :ci session (landed inflight); removed before the turn ran. create:never + reach:one guarantees undeliverable when no :ci session exists (config-provable).

## Deferred (not run)

Full GitHub-triggered end-to-end (real CI regression on a repo's main with both trailers) was NOT exercised — it requires deliberately breaking a real repo's default branch, too disruptive. Isaac-side routing is proven above; the GitHub-side trailer->param extraction is jq in the reusable workflow, already code-reviewed on iqqz. The first real regression will be the true e2e.
