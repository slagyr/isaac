---
# isaac-iqqz
title: 'ci-failure hail flow v2: direct-to-offending-session, correlation trailers, full debug params, band as template only'
status: completed
type: task
priority: high
created_at: 2026-07-03T18:08:15Z
updated_at: 2026-07-03T20:30:16Z
---

## Context (2026-07-03, Micah design review of live CI hail 17da7511)

The first live CI-failure hail exposed five defects; all settled decisions below.

## Decisions

1. **Direct the hail at the offending session.** Commits carry `Isaac-Session:` trailers (already extracted into params but unused). The workflow payload sets top-level `"session": <session_id>` when the trailer is present — explicit session trumps band selectors, band still supplies template + data. The session that pushed the break has the context to fix it. Band addressing is the fallback when no trailer.
2. **Drop `prompt` from the workflow payload.** The workflow currently sends a full prompt override, which defeats the band body template (`ci-failure.md` renders {{full_repo}} etc.) — double-maintained text. Params only; the band body is the single source of the instruction text.
3. **Band config**: remove `prefer :recent`; `create :never` (a fresh context-free session is worse than undeliverable); scope frequencies narrowly — dedicated `:ci` session tag (or crew filter) instead of the too-broad `:orchestration` (which is how the verifier session got drafted into CI repair). Deployment: tag the intended fallback sessions `:ci` on zanebot.
4. **`Isaac-Bean:` commit trailer** → `bean_id` param (extraction is symmetric with the session trailer). Band body gains the correlation instruction: if bean_id present or an in-progress bean scopes this repo, check its state first and reply into its thread / notify its owner — do NOT commission an independent repair. Worker skill (hail-bean-work) gains the commit-trailer convention (Isaac-Session + Isaac-Bean on implementation commits).
5. **Full debug params**: fetch the failed run's jobs via the GH API; include failing job name(s) and step(s) (+ run_id) so the crew can often skip opening run_url.

## Likely repo scope

orchestration repo: `.github/workflows/ci-failure-hail-reusable.yml`, `isaac-ci/config/hail/ci-failure.md`, `isaac-beans/prompts/skills/hail-bean-work/SKILL.md` (trailer convention). Zanebot: session tagging + band redeploy.

## Acceptance (one-time staged verification — GH workflow has no gherkin harness)

- [ ] Staged CI failure on a scratch commit carrying both trailers -> hail arrives DIRECTED at the named session, with bean_id, failing job/step params, and the band-rendered prompt (no override in the payload).
- [ ] Same staged failure without trailers -> falls back to the `:ci`-scoped band; no session created; undeliverable if no tagged session.
- [ ] Live CI-failure band on zanebot updated; orchestration + per-repo workflows in lockstep (per-repo ymls unchanged — inputs already suffice).
- [ ] hail-bean-work skill documents the trailer convention; redeployed.

## Implementation (work-3, 2026-07-03)

**orchestration** (pushed to `main`):

- `ci-failure-hail-reusable.yml` — `frequencies.session` when `Isaac-Session:` trailer present; `params.bean_id` from `Isaac-Bean:`; GH jobs API → `run_id`, `failing_jobs`, `failing_steps`; no prompt override.
- `isaac-ci/config/hail/ci-failure.md` — `:ci` tag, `create: :never`, correlation instruction, new template vars.
- `hail-bean-work/SKILL.md` — documents both trailers; README/REUSE updated.

**Verifier / ops follow-up:** redeploy band via `isaac-ci/install.sh` on zanebot, tag CI fallback sessions `:ci`, run staged scratch-commit verification per acceptance checklist.

## Verification notes (2026-07-03)

Verifier reviewed work-3 orchestration commit `6142770`.

Correct:
- The repo changes match the design intent: no prompt override, `Isaac-Session` drives direct routing via `frequencies.session`, `Isaac-Bean` becomes `params.bean_id`, and the band template now carries `run_id`, failing job names, and failing step names.
- `hail-bean-work` and the surrounding README / REUSE docs were updated to document the trailer convention.
- Orchestration CI for the implementation commit is green: GitHub run `28676946891` (`CI Tests`) completed successfully on 2026-07-03.

Missing:
- Acceptance is explicitly staged/live and was not completed. The bean body itself leaves verifier/ops follow-up undone: redeploy the band on zanebot, tag fallback sessions `:ci`, and run the scratch-commit verification checklist.
- There is no evidence in the repo or bean that the two staged CI-failure checks were run:
  - with both trailers, confirming direct delivery to the named session with `bean_id`, failing job/step params, and the band-rendered prompt
  - without trailers, confirming fallback to the `:ci`-scoped band with no session creation
- There is no evidence that the live zanebot band was updated/redeployed after the config and skill changes.

Implication:
- This bean is not verifiable as accepted yet. The code/doc changes look ready, but the required live deployment and staged end-to-end verification still need to be performed and recorded.



## Rescoped + completed (2026-07-03, planner)

The bean conflated crew-verifiable CODE with operator-only OPS, causing a verify fail-loop (staged CI failure, zanebot redeploy, :ci tagging cannot be done by autonomous crews). Split:

- CODE (this bean, DONE): work-3 commit 6142770 — reusable workflow trailer routing + jobs API params + no prompt override; ci-failure.md :ci tag/create:never/correlation; hail-bean-work trailer docs. Verifier confirmed correct + orchestration CI green (run 28676946891). Completing on the code contract.
- OPS rollout -> new bean isaac-cid1 (planner/operator executes): band redeploy, :ci session tagging, staged scratch-commit end-to-end verification.
