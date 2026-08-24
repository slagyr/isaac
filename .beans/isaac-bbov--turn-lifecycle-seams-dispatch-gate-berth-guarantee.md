---
# isaac-bbov
title: 'Turn lifecycle seams: dispatch gate berth + guaranteed finalization'
status: todo
type: task
created_at: 2026-08-23T19:19:16Z
updated_at: 2026-08-23T19:19:16Z
---

isaac-agent core seams extracted from the worksite design (2026-08-23, Micah). Prereq of worksite W1; also serves foreman turn-observation events and bh17 post-reply sealing.

## Decisions (2026-08-23, Micah + planning session)

1. **Guaranteed turn finalization.** One always-runs path wraps the ENTIRE turn — success, error reply, thrown exception, tool-loop exhaustion, provider failure: (a) record the turn outcome (isaac-e04k's record-closing-error folds in as the first citizen of this path, no longer a special case); (b) clear in-flight (KNOWN LEAK: silent-death forensics 2026-08-21 showed sessions left in-flight-looking — abnormal endings currently skip the clear); (c) settle turn markers; (d) invoke every release token acquired at dispatch (see 2). Order fixed: outcome first (never lose the record even if cleanup throws), then releases, then in-flight/markers; each step isolated so one failing cleanup cannot swallow the others (log + continue).
2. **Dispatch-gate berth `:isaac.agent/dispatch-gate`** at the dispatch entry (bridge/core seam): a registered gate receives the resolved dispatch context and returns one of {:resolve <ctx' + release-token>, :refuse <reason>, :pass}. ACQUIRE AND RELEASE ARE ONE CONTRACT — whatever a gate acquires it hands back as a release token; the finalization path invokes all tokens on every exit, guaranteed. Modules never hand-roll cleanup timing. Multiple gates compose in registration order; first :refuse wins.
3. **Refusal reasons are data** flowing to the caller unchanged (CLI: stderr + exit 1; hail: deferral, reason-agnostic — :worksite-busy joins :session-in-flight). Core interprets nothing.
4. **No behavior change with zero gates registered** — pure seam; existing suites are the regression net.

## Scenarios

None new — the seams are only observable through a plugged module; worksite W1's features (isaac-worksite repo) are the integration acceptance. This bean is spec-driven.

## Spec obligations (spec/isaac/drive or bridge)

- in-flight cleared when: tool throws mid-loop; provider returns 4xx/error; loop exhausts; turn body throws (the e04k silent-death shape — reuse its repro fixture).
- turn outcome recorded on all four shapes (extends e04k spec).
- gate contract: :resolve ctx flows into the turn (cwd override visible); :refuse short-circuits BEFORE any LLM dispatch (queued grover response not consumed) and surfaces reason; :pass no-ops; release token invoked on success AND on each failure shape; token invocation isolated (throwing token logs, doesn't break finalization); zero-gates = identity.
- multiple gates: registration order, first refuse wins, all acquired tokens released in reverse order.

## Acceptance

```
bb spec spec/isaac/drive spec/isaac/bridge spec/isaac/session
bb features features/bridge/cli-prompt.feature features/session features/episodes/live.feature
```
(all pre-existing suites green — the zero-gate regression net; no @wip to remove). Integration acceptance = worksite W1 features once that module exists.

DEPENDED ON BY: worksite W1 (gate + release), foreman turn-observation events (finalization is where turn-ended/turn-died events emit), isaac-bh17 post-reply sealing (same always-runs path).

## DESIGN REVISION (2026-08-24, Micah + planning session — supersedes the dispatch-gate-berth framing above)

The 2026-08-24 architecture session (recorded in isaac-tdgt and isaac-51xy) reshaped this bean's seam into THREE faces of one always-runs path:

1. **Guaranteed turn finalization** — unchanged from the original contract: one path wraps every turn outcome (success, error reply, thrown, loop exhaustion): record outcome, clear in-flight, settle markers, invoke release tokens, fire turn-observers. Order: outcome first, then releases, then observers, then in-flight/markers; each step isolated (log + continue).
2. **Turnstile protocol + NAMED registry** (replaces the dispatch-gate berth). A turnstile admits turns one at a time: `(admit? ctx) -> :pass | :hold | {:refuse reason}`; `:pass` yields a release token; finalization invokes tokens; a token firing is the wake signal for held requests. Turnstiles are SUBMITTED by clients, never ambient: the CLI's default is the null turnstile (run now or refuse); hail/foreman/cron submit their stacks. Implementations register BY NAME in an agent-core registry via manifest contribution (worksite contributes :worksite); submitters reference names as data (`:turnstiles [:worksite]`) so no module depends on another. **Registration is not activation.** Unknown name at submit = loud refusal; config validate checks turnstile names referentially.
3. **Turn-observer interface** — registered (not submitted): observers see EVERY turn regardless of submitter: turn-started, turn-ended {outcome}, turn-died {reason}, fired from finalization so they cannot be skipped. Foreman F2 is the first consumer (tables route "turn ended without signal" instead of humans reading transcripts).

**Invariants vs coordination (Micah's ruling)**: session single-writer and provider walls are CORE INVARIANTS — non-bypassable, not turnstiles. Turnstiles express coordination policy between actors and belong to the actors. Cost accepted: a submitter that omits a turnstile collides silently; the queue logs "gateless turn in a registered worksite" as a breadcrumb, never a block.

Spec obligations replace the gate-berth ones: turnstile protocol (pass/hold/refuse; token on every exit path; token isolation; null turnstile = identity), named registry (contribute/resolve/unknown-name refusal), observer firing on all four outcome shapes, observers isolated (a throwing observer logs, never breaks finalization), zero-turnstile zero-observer = no behavior change (existing suites as regression net).

DEPENDED ON BY (updated): the turn-request queue bean (next session — queue + addressing resolution + candidate selection consume this protocol), worksite W1 (first real turnstile), foreman F2 (first observer).
