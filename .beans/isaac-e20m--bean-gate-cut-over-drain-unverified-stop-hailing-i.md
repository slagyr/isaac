---
# isaac-e20m
title: 'Bean Gate: cut over — drain unverified, stop hailing isaac-verify'
status: in-progress
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-20T20:17:59Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
    - isaac-jp4v
    - isaac-przv
    - isaac-4b21
---

Repo: **isaac** (this repo). Child 5 of isaac-rmq6 — the cutover. Blocked by isaac-jp4v, isaac-przv and isaac-4b21.

## Why

With the gate documented (jp4v), the worker landing its own beans (przv) and CI re-checking them (4b21), the dual-run exists only to prove the new path. This bean ends it.

## Preconditions (check, do not assume)

- `beans list --tag=unverified` is empty. Any bean still carrying the tag goes through the **old** path first — do not convert it mid-flight.
- zanebot's `isaac-work` band loads `hail-bean-work-gate`.
- The snitch workflow has run green at least once on a real push.

## Change

1. **AGENTS.md** — the gate path becomes the stated default. The `unverified` + verify-hail path stays documented as what happens to a bean with no `feature-baseline`, which is now the exception rather than the rule. Remove the dual-run paragraph jp4v added; it has served its purpose.
2. **Planner watch rules** — record the new rule in the bean and in AGENTS.md: a gated bean needs no verify hail, so the watch dispatches work and then waits for `completed`; the stall rule still applies to a worker that goes quiet.
3. **Leave alone**: the `isaac-verify` band, the `perceptor` crew, the orchestration process-tests and their `orchestration-*` bands. They are not retired by this bean; a later decision covers them.

## Acceptance — one real bean proves it end to end

Not a document review. The bean is done when a bean with a `feature-baseline` has gone from dispatch to `completed` **without a verify hail**, and the snitch workflow passed on the push that completed it. Record in this bean: the bean id, its `main-sha`, the worker session, and the CI run URL.

If no gated bean is in flight when this one reaches that step, say so and hail the plan band — the planner supplies a small scenario-backed bean for the dogfood rather than the worker inventing one.

Also record what the first real run got wrong, if anything. That list is the whole point of running the dual path first.

Dispatched: hail aacf8ca7 2026-09-20T18:04:26Z (band isaac-work)



## Cutover status (2026-09-20, scrapper@isaac-work-1)

**Preconditions — checked, not assumed**

- `beans list --tag=unverified` → "No beans found." The drain is complete; the
  last ungated bean through the old path was isaac-ddls (handed to verify this
  morning, now `completed`).
- zanebot's `isaac-work` band loads the gate skill: `~/.isaac/config/hail/isaac-work.md`
  line 6 reads `Load and follow the "hail-bean-work-gate" skill.` (the pre-cutover
  file is kept as `isaac-work.md.bak-20260920-beangate`).
- Blockers all `completed`: isaac-cy85, isaac-jp4v, isaac-przv, isaac-4b21.
- Snitch workflow green on real pushes — but **only trivially** (see below).

**Change 1 + 2 — prepared, not landed**

`AGENTS.md` on branch `bean/isaac-e20m` @ `5b70abbcc3f78e49e359fcfcf2695bd6469956e2`
(base `origin/main@4049c2a3`), `bb ci` green (45 examples, 0 failures):

- `## Bean Workflow` now states the gated flow as the **default** and the
  `unverified` + `isaac-verify` flow as the **exception** for a bean with no
  `feature-baseline:`.
- The dual-run paragraph isaac-jp4v added ("Bean gate — verifier's role during
  the drain") is removed.
- Replaced by the planner watch rule: a gated bean needs no verify hail, so the
  watch dispatches work and then waits for `status=completed` with a `main-sha:`
  line; the stall rule is unchanged; a *gated* bean that turns up tagged
  `unverified` is a wrong close by the worker — treat it as a stall, not as
  verify's queue.

Held on the branch deliberately: the acceptance is a dogfood run, not a
document review, so the doc does not land ahead of the proof.

**Acceptance — blocked: no gated bean exists**

No bean in this repo has ever carried a real `feature-baseline:` line.

- `grep -n "^feature-baseline:" .beans/*.md` → one hit, and it is the literal
  template text inside isaac-cy85's own body (`feature-baseline: <repo> <module-main-sha>`),
  not a baseline.
- `bb bean-gate ci-scan HEAD~200 HEAD --edn` → `{:beans [] :skipped [… 67 beans …]}`;
  every skip is `:not-gated` or `:not-completed`. Nothing has ever been re-gated
  on main.

So the precondition "the snitch workflow has run green at least once on a real
push" is satisfied only in its empty form: all ten Bean Gate runs to date took
the `no completed, baselined bean in this push` branch and exited 0 without
cloning a module or calling `bb bean-gate verify`. The re-gate path — clone the
baselined repo, verify each id, fail the job on a non-zero verdict — has never
executed. **That is the first thing the dogfood must exercise.**

Per this bean's own instruction, the worker does not invent the dogfood bean:
hailed the plan band for a small scenario-backed bean to baseline and run
end to end.

**What the dual run got wrong so far** (the list this bean exists to collect):

1. The dual run never produced a gated bean, so it proved only the *old* path.
   The gate shipped (cy85), was documented (jp4v), got its worker skill (przv)
   and its CI snitch (4b21) without a single bean being baselined through them —
   the four children were all themselves ungated process beans.
2. Consequence: `bb bean-gate verify` has only ever been observed returning
   exit 2 in anger, and the snitch's re-gate path is unexercised in CI.
3. The verifier-side instruction jp4v added (record `bean-gate: pass|FAIL|not
   gated` in every bean) produced only `not gated` lines, by construction.

**Next step (resume here):** when the planner supplies the baselined dogfood
bean and it reaches `completed` with a green Bean Gate run, record here its
bean id, `main-sha`, worker session and the CI run URL, then land
`bean/isaac-e20m` and complete this bean.



## Planner adjustment (2026-09-20, prowl@isaac-plan) — dogfood bean isaac-2y86 baselined and dispatched

No gated bean existed, so the snitch's re-gate path never ran. Per this bean's instruction the worker does not invent the dogfood bean.

**Supplied:** **isaac-2y86** — Top-level `--help` lists `--version` / `-V`. One `@wip` scenario on isaac-foundation `features/cli/cli.feature:90` (main `f031ff2`). Baselined:

    feature-baseline: isaac-foundation f031ff2dcdabe681f6c75ac8e367e44d3a7960b0
    feature-blob: isaac-foundation features/cli/cli.feature e2f95fd2d79b2363297b1ee7fc757c2abbcf3f22 90

Dispatched to isaac-work. Gated close: worker implements, `bb bean-gate verify isaac-2y86`, lands, `completed`. **No verify hail.**

When isaac-2y86 reaches `completed` with a green Bean Gate CI run that actually cloned the module and called `bb bean-gate verify`, record here: bean id, `main-sha`, worker session, CI run URL — then land `bean/isaac-e20m`. Do **not** land the AGENTS.md cutover ahead of that proof.

Do not invent a second dogfood. Do not complete isaac-e20m on the empty snitch path.

Dispatched: hail 6848d4c8 2026-09-20T20:17:50Z (band isaac-work)
