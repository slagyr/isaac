---
# isaac-bbov
title: Turn finalization + turn-observer interface
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-23T19:19:16Z
updated_at: 2026-08-24T17:40:18Z
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

**Turnstile refs are parameterized** (2026-08-24, Micah): a submitted reference is a name OR name+params — `:worksite` (impl infers, e.g. member from turn cwd) or `[:worksite "chart-room"]` (explicit target). The registry resolves the name; params go to the impl. Same shape as foreman action refs.

**Observers come in both attachments** (2026-08-24, Micah): REGISTERED observers see every turn (foreman's system-wide watch); SUBMITTED per-turn observer refs ride a turn request with the same name+params shape as turnstiles — e.g. `[:foreman "bean-work" "bn-7"]` — so any submitter can wire one turn's lifecycle into a consumer's flow. `isaac prompt` grows `--observer` alongside `--turnstile` (e.g. `--observer foreman:bean-work/bn-7`): a hand-run CLI prompt can report into a foreman machine instance — manual turns become first-class citizens of an orchestration. Resolution, unknown-name refusal, and validate checks identical to turnstiles.

## SPLIT (2026-08-24, Micah): one bean per abstraction

This bean now carries **guaranteed finalization + the turn-observer interface** (registered + submitted refs, `--observer`). The **turnstile protocol** moved to **isaac-opp6** (blocked by this bean — release tokens ride finalization). Everything above about turnstiles is design history; isaac-opp6 is the implementation contract. Build order: bbov -> isaac-opp6 -> queue bean -> l3ps.

## Lookout + scenarios (2026-08-24, Micah)

Built-in observer **`:lookout`** (agent-owned): calls out `turn started` / `turn ended (ok)` / `turn ended (error ...)` on stdout — live narration for humans on long CLI turns AND the testable double for the observer interface. Name chosen over :crier/:telltale for implying continuous watching.

Scenarios @wip at isaac-agent b2f99a9, features/turn/observers.feature (3): lookout lines bracket the reply in order; failure path proves observers fire from finalization (outcome in the parentheses); unknown observer name (`foghorn:xyz`) refuses on stderr before dispatch — fresh queue entry consumed by the follow-up run proves the refused one was never eaten (abandoned deliberately, not reused). Registered-attachment (sees every turn, no flag) stays spec-level until foreman F2 gives it a real consumer. Line format pinned loosely — wording may grow (durations, tool counts) without breaking.

Acceptance gains: `bb features features/turn/observers.feature` (remove @wip).

## Verify fail (attempt 1, 2026-08-24): implementation is only on isaac-agent branch origin/bean/isaac-bbov and the acceptance feature remains @wip on origin/main, so acceptance is not yet landed/verifiable

Evidence:
- `git -C /Users/zane/agents/isaac/verify/isaac-agent log --oneline --grep='isaac-bbov' --all -n 30` shows implementation commits on `origin/bean/isaac-bbov` (`b0fa9bf`, `b2f99a9`).
- `git -C /Users/zane/agents/isaac/verify/isaac-agent rev-list --left-right --count origin/main...origin/bean/isaac-bbov` returned `0 1`: the bean branch is one commit ahead of main.
- `git -C /Users/zane/agents/isaac/verify/isaac-agent branch -r --contains b0fa9bf` does not include `origin/main`.
- `git -C /Users/zane/agents/isaac/verify/isaac-agent grep -n '@wip' origin/main -- features/turn/observers.feature` still shows `@wip` at lines 16, 27, and 39 on main, while the bean's acceptance says `bb features features/turn/observers.feature` with `remove @wip`.

Please land the isaac-agent work on main and re-hand off once the acceptance feature is no longer `@wip` on main.
