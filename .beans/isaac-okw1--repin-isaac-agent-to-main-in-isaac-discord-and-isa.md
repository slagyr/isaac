---
# isaac-okw1
title: 'Repin isaac-agent to main in isaac-discord and isaac-mcp (CI red: stale agent pin requires deleted config-cache; unreachable foundation via agent)'
status: completed
type: task
priority: high
tags:
    - ci
    - pins
created_at: 2026-09-19T03:38:55Z
updated_at: 2026-09-19T03:55:01Z
---

## Why

CI Tests on `main` is red in both repos for pin drift, not code:

| repo | pinned agent | symptom |
|---|---|---|
| isaac-discord | `8aecfc3` | `agent_steps.clj` requires `isaac.startup.config-cache`, deleted from foundation by isaac-f21o (09-15); foundation is pinned at `0b120cc` (post-f21o) so the namespace is gone → `FileNotFoundException` while loading `isaac.config.agent-steps` |
| isaac-mcp | `c827f23` | that agent's deps.edn pins foundation `1c8e45b`, reachable only from the squashed `bean/isaac-t1om` → `Error building classpath. Commit not found` |

isaac-lsz2 repinned foundation fleet-wide but left agent pins behind; isaac-agent main (`76320fa`) dropped the config-cache require (isaac-kk0o) and pins foundation main `df64bf1`. isaac-j4jr adds `bb lint-pins` so the unreachable case cannot recur; the stale-but-reachable case is what this bean fixes by hand.

## Do (each repo, one commit, worktree from origin/main — never the shared checkout)

- `deps.edn` and `bb.edn`: isaac-agent + isaac-agent-spec → `76320fab5d2c2ca614baa10a4a37d5fdec336e0c`; isaac-foundation, foundation-spec, foundation-test-support, marigold.* → `df64bf15c739165c496cbf81e3d922ad3aa3346f` (the sha agent main pins, so one foundation on the classpath). Any isaac-http pin → http main `d082206fa2a2e0099a892af863cb39016b7f3acd` only if the repo pins http at all.
- Pin rule (lsz2 §4): every isaac-* sha must be reachable from that repo's `origin/main` (`git merge-base --is-ancestor <sha> origin/main`).
- Fix whatever the newer agent/foundation surfaces in the repo's own specs/features; no feature-file edits beyond what the bean authorizes (`## Exceptions` below is empty on purpose — ask via the bean if a scenario must change).
- Bump each repo's version.

## Acceptance

```
cd isaac-discord && ISAAC_GIT=1 bb ci
cd isaac-mcp     && ISAAC_GIT=1 bb ci
```

CI Tests green on `main` for both repos after landing (link the runs in the bean). Order: mcp first (pure classpath fix), then discord.

## Exceptions

(none)

Dispatched: hail 987a7071 2026-09-19T03:39:45Z (band isaac-work)

## Worker progress (scrapper@isaac-work-2) — conflict, returning to planner

Pins applied (one commit each, worktrees from origin/main):

- isaac-mcp `bean/isaac-okw1` @ `801fb5f` (base origin/main@`121acf6`). Agent `76320fa`, foundation `df64bf1`, http `d082206`. Version 0.1.1. `bb spec` 32/1 — **pre-existing on origin/main**: `isaac.mcp.client` "returns a timeout error when catalog query is stare" NPE (reproduced after stashing pin edits). `bb features` 13/0. `ISAAC_GIT=1 bb ci` cannot be green without fixing that spec (not a pin problem; lsz2 already noted it).
- isaac-discord `bean/isaac-okw1` @ `5fff150` (base origin/main@`6e7e411`). Same pins. Version 0.1.15. `bb spec` 52/0. `bb features` 68/6 fail / 3 pending. Failures include:
  - `lifecycle.feature` / `service_lifecycle.feature` still use `server.port` — http `d082206` (tdlz) retires it (`:config/validation-error` path `server.port`). Bean `## Exceptions` is empty so worker did not recut tables.
  - `service_lifecycle` expected `:component/started` component discord, got `:module/activated`.
  - `splitting.feature` third POST `body.content` expected `"echo"`, got nil.
  lsz2 already called discord feature reds "genuine / leave to their own beans."

Conflict: acceptance is `ISAAC_GIT=1 bb ci` green on both; Exceptions empty forbids feature-file edits; mcp stare NPE is red on origin/main independently of this pin. Need planner Exceptions, a follow-up bean for the stare NPE / discord lifecycle, or a narrowed acceptance (pin-only).



## Planner adjustment (2026-09-19, prowl@isaac-plan) — pin-only; drop full bb ci

Conflict: pins are applied (mcp `801fb5f`, discord `5fff150`) but `ISAAC_GIT=1 bb ci` cannot go green without feature/spec edits empty `## Exceptions` forbids. MCP stare-timeout NPE is red on **origin/main independently of this pin**. Discord lifecycle/service_lifecycle still use retired `server.port` vs http `d082206`; splitting third POST nil; 3 pending episodes. lsz2 already called discord feature reds genuine.

**Decision: pin-only. Do not absorb the stare NPE or discord feature reds. Do not authorize feature-file edits on this bean. Do not require `ISAAC_GIT=1 bb ci` exit 0.** This bean exists to make the classpath build: agent `76320fa` + foundation `df64bf1` (+ http `d082206` where pinned), reachable from each repo's origin/main. Waiting on those reds leaves CI unable to even load.

### Ambient owners (not this bean) — draft, human promote

- **isaac-7b0g** (draft) — isaac-mcp `isaac.mcp.client` "returns a timeout error when catalog query is stare" NPE on origin/main (lsz2 @ 44c408d already 32/1).
- **isaac-tlv6** (draft) — isaac-discord `lifecycle` / `service_lifecycle` `server.port` → `:http :port` (tdlz); `:component/started` vs `:module/activated`; splitting third POST nil.

### Controlling acceptance (supersedes full bb ci)

**isaac-mcp** `bean/isaac-okw1` @ `801fb5f` (or rebased equivalent):

    git grep -n '76320fa\|df64bf1\|d082206' deps.edn bb.edn
    GITLIBS=/tmp/gl-okw1-mcp clojure -Sforce -Spath   # cold; no "Commit not found"
    bb spec   # record the stare NPE; do not fail this bean on it
    bb features   # 13/0 as already measured

**isaac-discord** `bean/isaac-okw1` @ `5fff150` (or rebased equivalent):

    git grep -n '76320fa\|df64bf1\|d082206' deps.edn bb.edn
    GITLIBS=/tmp/gl-okw1-discord clojure -Sforce -Spath
    bb spec   # 52/0
    # do NOT require bb features / bb ci exit 0

Pin rule: every isaac-* sha is reachable from that repo's origin/main. Version bumped. One commit per repo.

Do **not** require:

- isaac-mcp `bb ci` / `bb spec` 0 failures
- isaac-discord `bb features` / `bb ci` exit 0
- repairing the stare NPE
- recutting `server.port` tables
- filling `## Exceptions` for those files

`## Exceptions` stays empty on purpose.

### Worker now

1. Do not recut features or the stare spec.
2. Confirm pins + cold classpath + the named spec/feature counts above.
3. Hand to verifier. Do **not** land. Verifier records pin SHAs; does not fail on the filed ambient reds.

This note resets the verify-fail counter.

## Handoff (pin-only confirm, scrapper@isaac-work-1)

Confirmed planner pin-only acceptance. Did not recut features or the stare spec. Did not land.

**isaac-mcp** `bean/isaac-okw1 @ 801fb5f` (base origin/main@121acf6)
- pins: agent `76320fa`, foundation `df64bf1`, http `d082206` in deps.edn + bb.edn
- SHAs reachable from each repo origin/main (agent/foundation/http)
- cold classpath: `GITLIBS=/tmp/gl-okw1-mcp clojure -Sforce -Spath` — no "Commit not found"
- `bb spec` 32 examples, **1 failure** — stare NPE (`isaac.mcp.client lens fixture returns a timeout error when catalog query is stare`); recorded, not absorbed
- `bb features` 13/0

**isaac-discord** `bean/isaac-okw1 @ 5fff150` (base origin/main@6e7e411)
- same pins in deps.edn + bb.edn
- cold classpath: `GITLIBS=/tmp/gl-okw1-discord clojure -Sforce -Spath` — no "Commit not found"
- `bb spec` 52/0
- `bb features` / `bb ci` not required

Ambient reds remain for isaac-7b0g / isaac-tlv6. Verifier: do not fail on those.


## Landed on main (2026-09-19)

main-sha: isaac-mcp 75f51189a5d9f3dbda741e1789e0b44da1e9e8f9
main-sha: isaac-discord d92b94ed66f89ee1c9d003d328e5b8eeb8510fa0
