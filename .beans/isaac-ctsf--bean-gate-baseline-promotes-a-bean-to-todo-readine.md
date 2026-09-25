---
# isaac-ctsf
title: 'bean-gate: baseline promotes a bean to todo — readiness is the frozen contract, checked before dispatch'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-09-25T03:13:00Z
updated_at: 2026-09-25T14:19:36Z
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


## Verify fail (attempt 1, 2026-09-25): Required planner dispatch integration is missing: hail-bean-plan neither runs `bb bean-gate ready <id>` before `isaac-work` nor implements the documented ungated exception.

## Verify fail (attempt 2, 2026-09-25): `bean/with-file` resolves `.beans/isaac-ctsf--*.md` (a tracked literal wildcard worker-notes file) before the actual bean file. Consequently `bb bean-gate ready isaac-ctsf` reads no front matter and returns exit 1, `status unknown`, so valid beans cannot dispatch. Remove the stray wildcard file and make bean lookup select the actual bean filename; add a regression spec with both files.


## Planner adjustment (2026-09-25, prowl@isaac-plan) — lookup must not treat a literal `*` filename as the bean

Verify fail 2 is real. `bean/bean-file` does `(first (sort (fs/glob dir (str id "--*.md"))))`. babashka glob treats `*` as a wildcard, so both of these match `isaac-ctsf`:

- `.beans/isaac-ctsf--*.md` — a tracked literal filename (worker notes). Created in `52847a16` (Isaac-Session: isaac-work-1). Sort order puts `*` before letters, so this file wins.
- `.beans/isaac-ctsf--bean-gate-baseline-promotes-a-bean-to-todo-readine.md` — the actual bean.

`bb bean-gate ready isaac-ctsf` then reads a file with no front matter and exits 1 `status unknown`.

The accidental file is a worker session's notes, not a second bean. Do not keep it. Do not rename the real bean to dodge the sort.

### Worker now

1. Delete `.beans/isaac-ctsf--*.md` from the branch and from main if it is still there. Fold any unique note into the real bean body if it is not already there. Do not create another filename containing `*`.
2. `bean/bean-file` must select the bean file, not a literal-glob collision. A filename that is exactly `<id>--*.md` is not a bean. Regression spec: both files present, lookup returns the real bean (the one whose front matter is `# <id>`), and `bb bean-gate ready <id>` reads that status.
3. Keep the rest of the bean as written. Acceptance is unchanged. This repo stays ungated (`## Ungated` already says why).
4. `bb ci` green is not enough — the direct `bb bean-gate ready isaac-ctsf` smoke must exit on the real bean's status, not `status unknown`.

This note resets the verify-fail counter.

## Implementation notes (2026-09-25)

- Removed the accidental literal wildcard worker-notes file. Its only durable
  implementation observations are recorded by this bean's commits and the
  verification-failure history above.
- Bean lookup excludes a filename exactly matching `<id>--*.md`; regression
  coverage verifies lookup and `ready` use the real bean when both names exist.

## Verification handoff (2026-09-25)

branch: `bean/isaac-ctsf` @ `cd4e5bef5c4eb772b17f0318a7ef8780ad5172e5`
(base `origin/main@1ec49a4d248cb23be2c53459d6891223d0296ec1`).

Implemented the literal-wildcard lookup guard, removed the accidental tracked
worker-notes file, and added the lookup/CLI ready regression. Verification:
`bb ci` — 77 examples, 0 failures; `bb bean-gate ready isaac-ctsf` — exit 1
with `status in-progress` (not `status unknown`); `bb bean-gate verify
isaac-ctsf` — exit 2 (documented ungated path).
