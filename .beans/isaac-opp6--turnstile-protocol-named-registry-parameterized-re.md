---
# isaac-opp6
title: 'Turnstile protocol: named registry, parameterized refs, release tokens, prompt --turnstile'
status: completed
type: task
priority: normal
created_at: 2026-08-24T15:44:46Z
updated_at: 2026-08-24T21:51:10Z
---

Split from isaac-bbov (2026-08-24, Micah): admission is its own bean; finalization + observers stay in bbov. Blocked by bbov — release tokens need the always-runs finalization path.

## Contract (2026-08-24 design sessions — full arc in isaac-tdgt; bbov carries the shared history)

- **Turnstile = admission, one turn at a time** (the pun is load-bearing). Protocol: `(admit? ctx) -> :pass | :hold | {:refuse reason}`. `:pass` yields a release token; finalization (isaac-bbov) invokes tokens on every outcome; a token firing wakes held requests. Null turnstile = run-now (today's semantics).
- **Submitted, never ambient**: clients pass turnstile refs with their turn request. CLI defaults to null (`isaac prompt --turnstile worksite:chart-room` opts in; bare `worksite` = impl infers from cwd); hail/foreman/cron submit their own stacks. Coordination policy belongs to the actors.
- **Named registry, registration != activation**: impls register by name via manifest contribution (worksite contributes `:worksite` — isaac-l3ps); submitters reference names as data, name-or-[name params]. Unknown name at submit = loud refusal; `config validate` checks refs referentially. No module-to-module dependencies.
- **Invariants are not turnstiles**: session single-writer and provider walls stay core and non-bypassable.
- Accepted cost: a submitter that omits a turnstile can collide; log a breadcrumb ("gateless turn in a registered worksite"), never block.

## Scope

isaac-agent only: protocol ns, registry + manifest contribution key, submit-side ref resolution, `prompt` CLI `--turnstile` parsing, refusal surface (stderr + exit 1 at CLI). The queue (hold/wake) is the NEXT bean — until it lands, `:hold` at the CLI surfaces as refusal too (null-turnstile callers never hold).

## Scenarios

Planned in the queue-bean/W1-redraft session (the observable behavior needs a real turnstile — worksite's — so the sharp scenarios live in isaac-l3ps's redraft: locked member + --turnstile refuses, same prompt without it runs). This bean's own acceptance is spec-shaped: protocol conformance, null identity, registry resolve/unknown-name, ref parsing (name vs [name params], CLI colon syntax), token invoked on every exit path (rides bbov's finalization specs).

## Acceptance

```
bb spec spec/isaac/turnstile* spec/isaac/drive spec/isaac/bridge
bb features features/bridge/cli-prompt.feature
```
Pre-existing suites green (zero-turnstile = no behavior change). Integration acceptance = isaac-l3ps redrafted scenarios.

## Scenarios (2026-08-24, reviewed by Micah — @wip at isaac-agent bfdc7a9, features/turn/turnstiles.feature)

Built-in turnstile **`:tide`** — a clock-window turnstile (`--turnstile tide:22:00-06:00`): admits inside the window, `:hold` outside. The observers' lookout counterpart: test double AND real tool — quiet hours, overnight-only heavy jobs; with the queue bean it becomes the delayed prompt ("run at 22:00" = a request holding at a turnstile, no cron). Window may cross midnight (the scenario crosses it deliberately).

1. **tide holds a turn outside its window** — fixed clock 14:00: refused on stderr naming `tide`, the window, and `held`; exit 1; NO dispatch (the single queued echo survives); at 23:30 the same flag admits and the reply prints. Until the queue lands, a CLI `:hold` surfaces as this refusal — wording chosen to stay true once holds become parks.
2. **unknown turnstile names refuse loudly, before dispatch** — `--turnstile drydock:7`: stderr names it + `unknown turnstile`, exit 1; a following bare prompt (null turnstile default) runs and consumes the untouched response. Mirrors the observer registry's scenario on purpose — one rule for both registries.

New steps: none.

## Already landed (worker, before the planner hold): isaac-agent 3c25e89 — protocol, named registry, `prompt --turnstile`. Build on it; do not redo. Remaining: `:tide` impl (window parse incl. midnight crossing, uses the bound clock), CLI hold/unknown surfacing per the scenarios, drop @wip.

## Acceptance

```
bb features features/turn/turnstiles.feature
bb spec spec/isaac/turnstile* spec/isaac/drive spec/isaac/bridge
bb features features/bridge/cli-prompt.feature
```
(remove @wip; pre-existing suites stay green — zero-turnstile = no behavior change.)

## Planner note
Dispatched once without reviewed scenarios (planner's miss, 2026-08-24); recalled, scenarios reviewed, re-dispatched. Not the worker's error.



## Verify fail (attempt 1, 2026-08-24): acceptance still @wip and tide turnstile implementation is missing

Verified on isaac-agent HEAD bfdc7a9. `features/turn/turnstiles.feature` still marks both acceptance scenarios `@wip`, so `bb features features/turn/turnstiles.feature` ran 0 examples / 0 failures / 0 assertions instead of executable acceptance. Code inspection on HEAD also found no built-in `:tide` implementation in `src/` or supporting specs beyond the feature file text; `rg -n "turnstile|tide" src spec features` only surfaced existing generic registry/protocol support plus the new @wip feature. Bean contract explicitly requires tide/window behavior, CLI hold/unknown surfacing, and @wip removal before pass.

## Implementation (2026-08-24, scrapper@isaac-work-1)

Built on 3c25e89. Added built-in `:tide` (clock-window, midnight-crossing) registered via `ensure-builtins!`. Hold surfaces as `{status :hold, message "tide <window> held"}` so CLI stderr names tide, the window, and held. Bound clock: ctx `:now` else `memory/*now*` (feature `current time` step). SHA isaac-agent `2aad80e`. Dropped `@wip` on `features/turn/turnstiles.feature`.

Acceptance:
- `bb features features/turn/turnstiles.feature` — 2/0, 11 assertions
- `bb spec spec/isaac/turnstile* spec/isaac/drive spec/isaac/bridge` — 207/0, 482 assertions
- `bb features features/bridge/cli-prompt.feature` — 30/0, 61 assertions
