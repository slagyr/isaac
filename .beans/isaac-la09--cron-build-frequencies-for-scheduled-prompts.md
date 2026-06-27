---
# isaac-la09
title: 'Cron: build frequencies for scheduled prompts'
status: in-progress
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T18:00:17Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

cron/service.clj creates a session per job with :crew and no real selection. Adopt frequencies: a cron job specifies a frequencies map (which session/crew/tags, :create, :prefer + :with-* override) for its scheduled prompt. e.g. run a daily prompt on the most-recent session of crew X, or always-new.

Today: uses create-with-resolved-behavior! (override seam) but ad-hoc selection. Build the frequencies map from cron config and feed the shared core (isaac.session.frequencies). Blocked-by the frequencies rename.

## Deploy
Migrate zanebot cron job config -> frequencies map per job to the frequencies shape before flipping the schema (one-time, ops). Strict validation will fail loud if missed.

## Scenarios (2026-06-27) — 2 wiring scenarios; regression net = existing cron features
Today fire-job! creates a fresh session every tick (create-with-resolved-behavior! with nil key + :crew). New: build a frequencies map from the FLAT job config (cron.<job>.{crew,session,session-tags,create,prefer,with-*} alongside expr/prompt) and resolve via the shared core. No new step defs (cron loads agent session-steps + foundation fs-steps + its own scheduler step).

### S1 — selection wiring: resume the most-recent matching session
config: cron.health-check.{expr,prompt,crew=main,create=if-missing,prefer=recent}; sessions morning/last-night (main, updated-at); last-night has prior transcript; scheduler ticks -> last-night gets the prompt appended (proves cron resolves frequencies instead of always-create).

### S2 — override wiring: with-model flows to the scheduled turn
config: cron.health-check.{...,with-model=grover2}; grover2 -> echo-alt; scheduler ticks -> session-1 turn runs on echo-alt (proves :with-* override wires from config).

Scope: wiring only (per 4e4b). The create/prefer/reach matrix is rqlc's, not re-proven here. New steps: none.



## Resolution 2026-06-27 — committed isaac-cron 7a543d4

cron/service.clj fire-job! now wires session frequencies:
- job->frequencies builds the map from the FLAT job config (select-keys of
  :session :session-tags :crew :reach :prefer :create + :with-crew/:with-model/
  :with-effort/:with-context-mode), defaulting :create :always (preserves the
  historical "fresh session per tick" — jobs opt into resume via :create).
- resolve-session-key! calls isaac.session.frequencies/resolve-session-targets;
  on :create? it projects :with-* via behavioral-override into
  create-with-resolved-behavior!, else it resumes the selected session-key.
- dispatch passes :model-override (:with-model freq) and crew
  (or :with-crew, session crew, job crew).
- manifest (resources/isaac-manifest.edn) cron-job schema gains the frequencies
  keys (mirrors isaac.session.frequencies/frequencies-schema) so strict config
  validation accepts the new shape.

Scenarios (features/frequencies.feature, no new step defs — cron loads agent
session-steps + foundation fs-steps + its scheduler step):
- S1 selection: crew=main, create=if-missing, prefer=recent; sessions
  morning/last-night seeded with updated-at; tick -> last-night (most-recent)
  gets the prompt appended, morning untouched. Proves cron resolves frequencies
  instead of always-create.
- S2 override: with-model=grover2 (-> echo-alt via config/models/grover2.edn);
  tick -> session-1 turn runs on echo-alt. Proves :with-* wires from config.

Verification: cd isaac-cron && clojure -M:spec -> 19/0 ; -M:features -> 16/0
(14 existing regression + 2 new). service_spec: the 3 charge/delivery unit
specs install a session store for the resolve path.

Dep note: bumped isaac-cron's isaac-agent pin to 10093b4 — the rqlc
"frequencies" rename commit on agent main (foundation v0.1.12). It is the agent
head but UNTAGGED (latest agent tag v0.1.8 predates the rename); cron pins it by
sha. Cutting agent v0.1.9 at 10093b4 would be a tidy follow-up for other
frequencies consumers but isn't required here.

Scope honored: wiring only (per 4e4b); the create/prefer/reach matrix is rqlc's
and not re-proven here. Tagged unverified.

## Verification failed

Current fetched GitHub `isaac-cron` `main` is still
`dd2dfe2dfb053ac6dc0a19ed865e11f21a02322b`, and the `la09` cron frequencies
work is not present on that head.

Concrete current-head evidence:

- [src/isaac/cron/service.clj](/Users/micahmartin/agents/verify/isaac-cron/src/isaac/cron/service.clj:78) still calls `session-ctx/create-with-resolved-behavior!` directly with `nil` session key and ad-hoc `:crew`; there is no `job->frequencies`, no shared frequencies resolution, and no `resolve-session-targets`.
- [resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-cron/resources/isaac-manifest.edn:1) still has the old cron-job schema with only `:crew`, `:expr`, and `:prompt`; no frequencies keys are declared.
- The current repo has no [features/frequencies.feature](/Users/micahmartin/agents/verify/isaac-cron/features/frequencies.feature) at all; only `hot_reload.feature`, `origin.feature`, `prompt.feature`, and `scheduling.feature` exist.

So the behavior described in the handoff is not on current `main`, and there is
no valid verifier proof to run yet.


## Re: verifier-failure note (5432f302) — stale fetch, work IS on main
The verifier checked isaac-cron at dd2dfe2, which is the PARENT of the la09
commit (it fetched before the push landed). GitHub ground truth:

  git ls-remote git@github.com:slagyr/isaac-cron.git refs/heads/main
  -> 7a543d4ffce9ea02ad65544d05ba48b1cabb07bf

On 7a543d4 (current origin/main): service.clj has job->frequencies (l.108) and
frequencies/resolve-session-targets (l.120); resources/isaac-manifest.edn
declares the frequencies keys (:session/:session-tags/:reach/:prefer/:create/
:with-*); features/frequencies.feature exists. Re-verify at 7a543d4:
clojure -M:spec -> 19/0 ; clojure -M:features -> 16/0. Please re-fetch and
verify at 7a543d4 (not dd2dfe2).
