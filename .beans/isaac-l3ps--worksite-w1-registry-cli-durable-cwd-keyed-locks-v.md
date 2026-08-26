---
# isaac-l3ps
title: 'Worksite W1: registry, CLI, durable locks via :worksite turnstile'
status: completed
type: task
priority: normal
created_at: 2026-08-23T19:23:46Z
updated_at: 2026-08-26T07:46:23Z
---

First bean of the isaac-worksite module (repo slagyr/isaac-worksite, skeleton at ac40655, scenarios @wip at 75137cf). Design record: isaac-51xy decisions 36-38, isaac-tdgt addenda, planning session 2026-08-23.

## Decisions (2026-08-23, Micah + planning session)

1. **W1 locks key on CWD, not on hail bands.** Any turn whose cwd equals a registered member is gated — hail/CLI/comm/cron alike; zero hail or band-config changes. Registration alone activates protection. Logical pool binding ("send to the isaac-work pool") is W2. Subdirectory containment (cwd INSIDE a member tree) = spec obligation, exact-match is the scenarioed contract.
2. **Locks are durable from day one** (lock files; CLI turns are separate processes — an atom cannot serve). Pulled forward from W3, whose scope narrows to lease EXPIRY/heartbeat semantics. Lock file: {:kind :operator|:turn :holder <pid> :session <id> :at <ts>} under the isaac root (exact path is implementation detail; steps assert via CLI/list only).
3. **Stale turn locks are broken on pid-death** (worksites are per-host, pid liveness suffices): gate finding a turn lock with a dead holder steals it with a LOUD log. **Operator locks are NEVER auto-broken** — a human said stop; only a human says go. Live-but-hung holders keep their locks (hung-vs-working is e04k-watchdog/W3 territory).
4. **Both config forms** (root :worksites + config/worksites/<name>.edn entity files, :merge-root-entity?); :members vector required — singleton = 1-member pool, so W2 adds allocation, not migration.
5. Gate plugs isaac-bbov's :isaac.agent/dispatch-gate berth via manifest contribution; refuse-only in W1 (+ release token); resolution/allocation arrives in W2. Refusal reason :worksite-busy — hail defers on it via the existing reason-agnostic path.
6. CLI `isaac worksites list|lock|unlock` via the module's :isaac/cli manifest contribution — first CLI from a non-builtin module repo.

## Scenarios (2026-08-23, committed @wip at slagyr/isaac-worksite 75137cf)

features/worksite/registry.feature (3): both-forms validation + memberless rejection; merged list with lock state + help surface; unregistered cwd untouched (only worksite locked, turn elsewhere sails through — deployment-safety guard).
features/worksite/lock.feature (5): operator lock/unlock round-trip across five processes (durability implicit); locked worksite refuses dispatch loudly BEFORE any LLM call (queued response survives to post-unlock run) and unlock frees; turn lock releases at turn end (anti-wedge with free assertion between turns); error turn (http 403) still releases — the isaac-bbov finalization observed end-to-end; stale dead-pid turn lock broken + operator lock contrast.

## Step ledger

| step | status |
|---|---|
| Isaac root / config file docstring / isaac EDN file table / config validate / default Grover setup / sessions exist / model queue incl. http-error rows / prompt runs / stdout-stderr-exit | reuse |
| **Given a stale turn lock holds worksite {string} with pid {int}** | **NEW — forges a :turn-kind lock file with a dead pid; only a crash produces this state, no step writes lock internals** |
| the isaac EDN file ... exists with: | reuse, VERIFY the cell parser accepts an EDN vector cell (["/ships/..."]); fallback = foundation docstring file step — confirm, don't invent |
| the following sessions exist: | reuse, VERIFY the table accepts a cwd column; if not, one-column addition to the existing step (reuse-with-revision) |

## Production notes

- Module layout per skeleton: src/isaac/worksite/{registry,lock,gate,cli}.clj suggested; manifest already carries the :worksites config schema (root+entity). Add :isaac/cli + :isaac.agent/dispatch-gate contributions to resources/isaac-manifest.edn.
- BLOCKED BY isaac-bbov (gate berth + guaranteed finalization must exist in isaac-agent first); pin deps.edn/bb.edn agent SHAs to the bbov release.
- Spec obligations: subdirectory containment; lock-file round-trip vs independently-computed content (decision-8 discipline); pid-liveness check; steal logs loudly; concurrent-take atomicity (file create exclusive).

## Acceptance

In slagyr/isaac-worksite — remove @wip and:
```
bb spec spec
bb features features
```
Plus isaac-agent suites stay green under the bbov pin (zero-gate net):
```
bb features features/bridge/cli-prompt.feature features/session
```
Field check on zanebot (recorded here): register isaac-work-1's checkout as a worksite; operator-lock it; send a bean hail; watch the delivery DEFER (not dead-letter) until unlock; unlock; watch it flow. That demo is W1's whole reason to exist.

## DESIGN REVISION (2026-08-24, Micah + planning session)

**Cwd-keyed AUTO-gating is OUT** (decision 1 above superseded): protection is opt-in via the turnstile abstraction (see isaac-bbov revision). Worksite implements the `:worksite` turnstile and contributes it BY NAME to agent's registry; hail and foreman include it in their default stacks; the CLI defaults to the null turnstile — the human at the keyboard is the authority the lock protects. `worksites lock` now means "robots out": hail defers, foreman parks, the operator keeps working via CLI. Optional `--turnstile worksite` CLI flag for cautious operators.

STANDS UNCHANGED: registry + both config forms, `worksites list/lock/unlock` CLI, durable lock files, operator-lock sanctity, dead-pid stealing, W2/W3 split.
SCENARIOS 4, 6, 8 SUPERSEDED — redraft next planning session around explicit gating (sharpest form asserts both directions: `prompt --turnstile worksite` in a locked member refuses; the same prompt without it runs). Scenarios 1-3, 5, 7 stand. Field demo becomes: lock -> hail defers -> CLI still works -> unlock -> hail flows.
BLOCKED-BY updated: isaac-opp6 (turnstile protocol) and the turn-request queue bean; isaac-bbov transitively.

**CLI option** (2026-08-24, Micah): `isaac prompt` grows an opt-in turnstile flag; agent owns both the CLI and the abstraction, so no berth needed. Value must carry kind+argument — proposed `--turnstile worksite:chart-room` (bare `--turnstile worksite` = infer member from cwd). Scenario redraft pins the exact syntax.

## Conflict (2026-08-25, scrapper@isaac-work-2)

Claimed and surveyed; no product code written. Returning to planner — the bean cannot satisfy its own acceptance without rewriting approved features.

**What stands vs what is committed**

- Design revision (2026-08-24) **kills cwd-keyed AUTO-gating**. Protection is opt-in via the `:worksite` turnstile; CLI defaults to null so a human at the keyboard keeps working while `worksites lock` means robots out. Field demo: lock → hail defers → CLI still works → unlock → hail flows.
- `slagyr/isaac-worksite` HEAD `75137cf` still has the original 8 @wip scenarios. lock.feature:42 ("a locked worksite refuses dispatch") runs `prompt` **without** `--turnstile` and expects stderr locked + exit 1. lock.feature:67 and :92 take a turn lock automatically on every CLI prompt in a member cwd and assert release. lock.feature:118 forges a stale turn lock and expects the next ungated prompt to steal it.
- Bean text: "SCENARIOS 4, 6, 8 SUPERSEDED — redraft next planning session around explicit gating (sharpest form asserts both directions: `prompt --turnstile worksite` in a locked member refuses; the same prompt without it runs). Scenarios 1-3, 5, 7 stand. Scenario redraft pins the exact syntax."
- That redraft never landed. gherclj/work.md forbid rewriting approved `.feature` contracts. Implementing auto-gating would violate the revision. Leaving @wip would fail acceptance (`remove @wip` + `bb features features`).

**Numbering is also ambiguous.** Registry 1-3 + lock 5 scenarios = 8. "4, 6, 8 superseded" vs "1-3, 5, 7 stand" does not line up with "STANDS UNCHANGED: worksites list/lock/unlock CLI" (lock.feature:15 is the operator round-trip). Planner needs to pin which of the eight stay, and rewrite the rest.

**Also unresolved for W1**

- Exact `--turnstile` syntax in the redraft (`worksite` infer-from-cwd vs `worksite:chart-room`).
- Whether a locked worksite `:hold`s (park on the ohsy queue) or `{:refuse :worksite-busy}` (hail defer). Revision says hail defers; ohsy parks `:hold`. CLI with `--turnstile worksite` while locked is the sharp refuse/hold case.
- Turn-lock acquire-on-admit + release-on-token: still in W1 for `--turnstile worksite` (dead-pid steal, operator sanctity), or deferred?
- Hail/foreman default stacks including `:worksite` — W1 or a later hail/foreman bean?
- Pin `isaac-worksite` deps.edn/bb.edn agent SHA to post-ohsy `2c87d0d` (current pin is `10093b4`). Blockers bbov + opp6 are completed; ohsy is in-progress+unverified on origin/main.

Checkout: `/Users/zane/agents/isaac/work-2/isaac-worksite` cloned at origin/main@75137cf. No impl branch. Claim 98e90c11.


## Planner adjustment (2026-08-25, prowl@isaac-plan) — conflict resolve + feature redraft

**Conflict accepted.** Worker was right: original lock scenarios encoded AUTO-gating; revision killed that. Redraft is landed; implement against the new contract.

### Features redrafted and pushed

`slagyr/isaac-worksite` `origin/main@d7ae11d` (was `75137cf`).

| # | File:scenario | Status |
|---|---|---|
| R1 | registry: both-forms validate + memberless rejected | **STANDS** (prose: no auto-gate claim) |
| R2 | registry: list/help + lock state | **STANDS** |
| R3 | registry: outside worksite sails through while another locked | **STANDS** (null-turnstile path) |
| L1 | lock: operator lock/unlock round-trip | **STANDS** (was "scenario 5") |
| L2 | lock: locked member refuses `--turnstile worksite`; bare prompt still runs | **REWRITTEN** (was auto-refuse ungated prompt) |
| L3 | lock: turn lock via `--turnstile worksite` releases at end | **REWRITTEN** (was auto-take on every CLI prompt) |
| L4 | lock: failed turn still releases | **REWRITTEN** (same; requires `--turnstile worksite`) |
| L5 | lock: dead-pid steal with `--turnstile worksite`; operator never auto-broken | **REWRITTEN** (was ungated steal) |

Old numbering "4,6,8 superseded / 1-3,5,7 stand" is retired. Use R1–R3 + L1–L5 above.

### Design pins (W1)

1. **`--turnstile` syntax**
   - `worksite` — infer member from session cwd (primary in scenarios).
   - `worksite:<name>` — explicit worksite name (supported by opp6 parse; optional scenario not required for W1 green).
2. **Locked decision = `{:refuse :worksite-busy}`** (not `:hold`).
   - CLI with `--turnstile worksite` while locked → stderr + exit 1 (no park).
   - Hail deferral consumes the reason (existing reason-agnostic path).
   - ohsy park-on-`:hold` is for other turnstiles (tide, admits-N); worksite lock is refuse.
3. **Turn-lock acquire-on-admit is W1** for submitted `:worksite` only.
   - Operator lock/unlock CLI, durable files, dead-pid steal, operator sanctity, release-on-finalization token — all W1.
   - Null-turnstile / bare CLI never takes or consults the turn lock.
4. **Hail/foreman default stacks including `:worksite` are NOT W1.**
   - W1 ships the named factory + CLI opt-in + lock/list/unlock.
   - Default stack wiring = later hail/foreman beans (field demo "lock → hail defers" needs that follow-up; do not block W1 acceptance on it).
   - Record field demo as post-W1 when hail default stack lands.
5. **Agent pin:** bump isaac-worksite `deps.edn` / `bb.edn` isaac-agent (+ agent-spec) pins to **`2c87d0d`** (post-ohsy on agent main at redraft time) or newer main that still carries bbov+opp6+ohsy turnstile/queue seams. Current pin `10093b4` is too old.

### Manifest / product shape (W1)

- Contribute `:worksite` turnstile factory via `:isaac.agent/turnstiles` (not the old dispatch-gate berth framing).
- `:isaac/cli` for `worksites list|lock|unlock`.
- No ambient gating. Registration ≠ activation for turns.

### Blockers

- **bbov** completed, **opp6** completed.
- **ohsy** in-progress+unverified at agent `2c87d0d` — W1 does not require ohsy green for refuse path; pin to that SHA (or later) for turnstile+finalization APIs. If ohsy regresses agent main, wait or pin a known-good SHA that still has opp6.
- Remove stale `blocked_by: isaac-bbov` only; do not hard-block on ohsy verify unless pin is unavailable.

### Acceptance (supersedes prior)

In `slagyr/isaac-worksite` at a SHA that includes `d7ae11d` features + W1 impl:

```
bb spec spec
bb features features
```

Remove `@wip` from all eight scenarios; 0 failures.

Agent suites under the bumped pin (zero-turnstile regression net):

```
bb features features/bridge/cli-prompt.feature features/session
```

(or document equivalent green on the pinned agent SHA).

### Out of scope for W1

- Hail/foreman auto-submit of `:worksite`
- ohsy hold/wake integration with worksite
- W2 pool allocation / logical pool addressing
- W3 lease expiry/heartbeat

### Worker handback

Implement against redrafted features at worksite `d7ae11d`+. Bump agent pin. Do not restore auto-gating. Hand verify when `@wip` cleared and suites green.
