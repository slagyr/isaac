---
# isaac-opp6
title: 'Turnstile protocol: named registry, parameterized refs, release tokens, prompt --turnstile'
status: todo
type: task
created_at: 2026-08-24T15:44:46Z
updated_at: 2026-08-24T15:44:46Z
blocked_by:
    - isaac-bbov
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
