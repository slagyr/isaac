---
# isaac-l3ps
title: 'Worksite W1: registry, CLI, durable cwd-keyed locks via the dispatch gate'
status: todo
type: task
created_at: 2026-08-23T19:23:46Z
updated_at: 2026-08-23T19:23:46Z
blocked_by:
    - isaac-bbov
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
BLOCKED-BY updated: isaac-bbov (revised) and the turn-request queue bean.
