---
# isaac-iqqz
title: 'ci-failure hail flow v2: direct-to-offending-session, correlation trailers, full debug params, band as template only'
status: todo
type: task
priority: high
created_at: 2026-07-03T18:08:15Z
updated_at: 2026-07-03T18:08:15Z
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
