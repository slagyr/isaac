---
# isaac-opp6
title: 'Turnstile protocol: named registry, parameterized refs, release tokens, prompt --turnstile'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-24T15:44:46Z
updated_at: 2026-08-24T21:39:32Z
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

## HOLD — planner recall (2026-08-24)

Dispatched prematurely without reviewed scenarios (planner's miss; Micah caught it). **Workers: do not build this bean until this note is replaced by a scenario section and status returns to todo.** If you have already claimed it, stop, leave no changes, un-claim, and reply to your hail that opp6 is on planner hold.

## Unclaimed (planner hold, 2026-08-24)

**scrapper**@isaac-work-1 unclaimed per hail e65676da. No further opp6 work. Implementation commit isaac-agent@3c25e89 was already on origin/main before this hold arrived; no revert (hold said discard uncommitted edits only). Verify hail 9503b62c was already sent on the prior work turn — do not treat as a fresh handoff.


## HOLD cleared (2026-08-24, prowl@isaac-plan) — verify may proceed

**Previous HOLD is superseded.** Workers may treat opp6 as active again. Verification proceeds against the already-landed implementation on isaac-agent `origin/main@3c25e89` (`isaac-opp6: turnstile protocol, named registry, prompt --turnstile`). No rebuild required unless verify finds a contract gap.

### Why the hold existed

Dispatched to work without a reviewed scenario section (planner miss). Micah correctly stopped further build. Implementation had already landed on main before the hold arrived; it was not reverted (hold only discarded uncommitted edits).

### Why verify can proceed now

This bean was always **spec-shaped**. The original body said so: protocol conformance, null identity, registry resolve/unknown-name, ref parsing, token on every exit path. Integration features belong to **isaac-l3ps** (real `:worksite` turnstile), not opp6. That is the scenario section — restated below as falsifiable obligations matching `3c25e89`.

bbov (finalization + release-token path) is **completed**, so the dependency is satisfied.

### Spec / scenario obligations (acceptance contract)

Covered under isaac-agent (no new feature file required for this bean):

| Obligation | Where |
|---|---|
| Protocol `admit?` → `:pass` / `:hold` / `{:refuse reason}` | `spec/isaac/turnstile_spec.clj` |
| Null turnstile = identity (run-now) | turnstile_spec + zero-turnstile bridge/drive |
| Named factory registry register/resolve/unregister | turnstile_spec |
| Submitted refs: name or `[name params]`; CLI `name` / `name:params` / slash params | turnstile_spec + prompt_cli_spec |
| Unknown submitted name refuses **before** dispatch | turnstile_spec + bridge_spec |
| `:hold` surfaces as refuse until queue bean | turnstile_spec |
| Release tokens collected on `:pass`; released reverse-order on every exit; throwing release isolated | turnstile_spec + drive/turn_spec (bbov finalization) |
| Zero turnstiles = no behavior change | bridge/cli-prompt + drive/bridge suites |
| Manifest berth `:isaac.agent/turnstiles` contribution registers factories | turnstile_spec register-entry! |
| Gateless breadcrumb log when factories exist but charge submits none | bridge path (`:turnstile/gateless`) — soft, not a hard fail if missing from acceptance cmds |

### Acceptance (verify these)

On isaac-agent at a SHA containing `3c25e89` (or successor that still carries the turnstile work):

```
bb spec spec/isaac/turnstile* spec/isaac/drive spec/isaac/bridge
bb features features/bridge/cli-prompt.feature
```

0 failures. Pre-existing pending only if listed and unrelated.

Out of scope for this bean (do not fail opp6 on these):
- Real worksite turnstile behavior / locked-member features → **isaac-l3ps**
- Hold/wake queue → next bean after opp6
- Ambient turnstiles (explicitly never — submitted only)

### Status

Hold lifted. Implementation candidate is main@3c25e89. Re-hailing **verify** (not work). If verify finds a real contract hole vs the table above, fail back to work with the gap; do not re-impose this hold.
