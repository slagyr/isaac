---
# isaac-ctsf
title: 'bean-gate: baseline promotes a bean to todo — readiness is the frozen contract, checked before dispatch'
status: todo
type: feature
priority: high
created_at: 2026-09-25T03:13:00Z
updated_at: 2026-09-25T03:13:00Z
---

## Why

Micah, 2026-09-25: the baseline is what makes a bean ready — "it's probably
the bean gate baseline that should put the bean in the to-do state." Today
`todo` is set by hand (`beans update --status=todo`, plan.md step 3) and the
baseline is a separate planner step nothing enforces. Every bean the planner
filed on 2026-09-24/25 reached workers with no `feature-baseline:` line and
took the ungated fallback (unverified → verify crew), which is how @wip'd
scenarios got landed on isaac-aswr.

## Design

1. **`bb bean-gate baseline <id> <repo>:<path>[:<line>…]…` promotes.** After
   writing the `feature-baseline:` / `feature-blob:` lines it sets the bean's
   front-matter `status` to `todo`. Allowed from `draft` or `todo` only; on
   `in-progress` / `completed` / `scrapped` it exits 2 with a message and
   changes nothing. With no refs it exits 2 ("a bean is baselined against
   scenarios; none given") and does not touch status.
2. **`bb bean-gate ready <id>`** — exit 0 when the bean is `todo` AND carries
   at least one `feature-baseline:` line; exit 1 with a one-line reason
   otherwise (`not baselined`, `status draft`, …). Pure read.
3. **Dispatch checks readiness.** The hail-bean-plan skill and
   `isaac/.toolbox/commands/plan.md` run `bb bean-gate ready <id>` before
   hailing `isaac-work`; a non-zero exit stops the dispatch and reports the
   reason. A bean whose module has no feature runner (this repo, for
   instance) is the documented exception: it is dispatched ungated with a
   `## Ungated` note in the body saying why.
4. **Docs, three places, same sentence:** "A bean is `todo` only when
   `bb bean-gate baseline` has frozen its scenarios; the baseline command
   sets the status." — `isaac/AGENTS.md` (Bean Workflow → status flow, and
   Planning), `isaac/.toolbox/commands/plan.md` (step 3 replaces the manual
   status update), `plan/AGENTS.md` (planner readiness rule; the
   `hail-bean-plan` skill links to it rather than restating).

## Acceptance (isaac spec — this repo has no feature runner, so the bean is
## ungated by the exception in (3); spec coverage instead)

- [ ] `main_spec`: baseline on a `draft` bean writes the gate lines and the
  front matter reads `status: todo`; on a `todo` bean status stays `todo`.
- [ ] baseline on `in-progress` / `completed` / `scrapped` → exit 2, file
  byte-identical.
- [ ] baseline with no refs → exit 2, status unchanged, no gate lines added.
- [ ] `ready`: `todo` + baseline line → 0; `todo` without → 1 "not
  baselined"; `draft` with baseline line → 1 "status draft".
- [ ] The three docs carry the sentence; plan.md step 3 no longer says
  `beans update --status=todo`; `bb spec` and lint green.

## Ungated

isaac (this repo) has speclj specs and no gherkin runner, so there is nothing
to baseline against. Dispatched ungated on purpose; verify path applies.

Likely repo scope: isaac (src/isaac/bean_gate/main.clj + core, spec,
AGENTS.md, .toolbox/commands/plan.md, .toolbox/skills/hail-bean-plan) and
plan/AGENTS.md (planner home — not a git repo; planner edits it by hand).
