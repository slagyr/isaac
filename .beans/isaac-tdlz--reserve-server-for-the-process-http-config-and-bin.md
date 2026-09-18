---
# isaac-tdlz
title: Reserve :server for the process; HTTP config and bind logs move to :http
status: in-progress
type: feature
priority: high
tags:
    - http
    - config
    - server
    - unverified
created_at: 2026-09-18T01:34:41Z
updated_at: 2026-09-18T03:33:12Z
parent: isaac-3q4m
---

Clean cutover. `isaac server` the command and `logs/server.log` stay. `:server` in isaac.edn and bind fields on `:server/started` do not.

Parent: isaac-3q4m (server means the process). isaac-s9e3 already landed live token reload on the old `:server :auth :token` path; this bean renames that path.

## Decisions (2026-09-17, Micah)

1. **`:http {:host :port :auth :burst}`** — isaac-http schema.
2. **Top-level `:hot-reload`** — not `:config {:hot-reload}` (`isaac config set config.hot-reload` stutters). Foundation schema.
3. **`:bridge {:suspend-timeout-ms}`** — agent schema. Bridge already owns suspend.
4. **Foundation owns the retired `:server` table** (all former leaves, `[:retired? "use …"]`). One owner for the shell. HTTP does not keep contributing `:server`.
5. **`:server/started`** — process is up, no host/port. Emitted from `runner/start!`, not from `runner.cli` copying the HTTP component's bound port.
6. **`:http/listening`** — host and port. Emitted by the HTTP component after bind.
7. CLI `isaac server -p/-H` stay as process flags that override `:http` bind.
8. `features/server/` directory and `:server-runtime` component rename are a later pass.

## Implementation

- Foundation: top-level `:hot-reload`; retired `:server` map; move `:server/started` off `runner.cli` onto `runner/start!` (drop host/port); log-stream description is process logs, not "HTTP server logs".
- isaac-http: contribute `:http`; read `[:http …]` instead of `[:server …]`; log `:http/listening` after bind; sweep `server.host` / `server.auth.token` / `server.hot-reload` features and `cfg-fn` paths (including isaac-s9e3's live token, now `[:http :auth :token]`).
- Agent: `:bridge :suspend-timeout-ms`; stop reading `[:server :suspend-timeout-ms]`.
- isaac-hooks: `hooks.auth.token` retired hint becomes `use :http :auth :token`.
- Other modules: feature/config fixtures that still say `server.*`.

## Land order

1. isaac-foundation
2. isaac-http
3. isaac-agent, isaac-hooks, then remaining modules that mention `server.host` / `server.auth` / `server.hot-reload`
4. isaac registry only if a pin train is required

## Acceptance

@wip on each new feature file.

- `isaac-http` `features/http/config.feature:9` — :http bind/auth config is valid
- `isaac-http` `features/http/config.feature:22` — Retired `<key>` fails pointing at the new slot (outline)
- `isaac-foundation` `features/cli/server.feature:9` — Process start logs :server/started without host or port
- `isaac-http` `features/http/listening.feature:9` — HTTP bind logs :http/listening with host and port
- `isaac-http` `features/http/listening.feature:20` — Default port 6674 is on :http/listening

Superseded and removed from `isaac-http` `features/server/command.feature`: "server command logs startup with host and port" and "Default port is 6674 when no port is configured". Keep the hello scenario.

```
cd isaac-foundation && bb features features/cli/server.feature:9
cd isaac-http && bb features features/http/config.feature features/http/listening.feature
cd isaac-http && bb spec spec/isaac/http
cd isaac-foundation && bb spec spec/isaac/runner spec/isaac/main_spec.clj
cd isaac-http && bb ci
cd isaac-foundation && bb ci
```

Done when `@wip` is gone from those three files and the commands are green. Existing `server.*` fixtures in other modules must load or they are in this bean's sweep.

## Non-goals

Rename `isaac server`. Rename `logs/server.log` / `isaac logs server`. Dual-token overlap. Live rebind of host/port (isaac-03wy). `:server-runtime` rename. `features/server/` directory rename. Stopping `isaac config set` from logging token values.

## Work checkpoint (2026-09-18, scrapper@isaac-work-3)

Done:

- Foundation `bean/isaac-tdlz` @ `c5ac437` (base `origin/main@cc53d69`): base schema owns top-level `:hot-reload` and retired `:server` leaves; `runner/start!` emits process-only `:server/started`; runner CLI no longer adds bind fields; process log stream description updated. Focused schema/runner specs and `features/cli/server.feature:9` green.
- HTTP `bean/isaac-tdlz` @ `22d4c04` (base `origin/main@1245bfb`): manifest contributes `:http`; runtime reads HTTP host/auth and top-level hot reload; handler reads live `:http` auth/burst; listener emits `:http/listening`; duplicate HTTP `:server` CLI owner and old startup log removed; harness/config specs migrated; Foundation branch pinned. Green: listening feature (2/3 assertions), config valid scenario (1/4), all `spec/isaac/http` (102/170).
- Agent `bean/isaac-tdlz` @ `a38d403`: `:bridge :suspend-timeout-ms` schema/read and component specs green.
- Hooks `bean/isaac-tdlz` @ `b764fd6`: retired hint and feature fixtures point to `:http`.

Red / next:

- Full HTTP config feature cannot parse its planner-authored Scenario Outline example strings under gherclj 1.3.0 (quoted EDN is injected into generated test names/forms). First scenario is green; retire validation is covered by Foundation schema spec. Do not rewrite the feature beyond authorized `@wip` removal without verifier/planner approval.
- Resume remaining fixture sweep at `isaac-claude-code/src/isaac/llm/api/claude_cli.clj:374` (running server port/token map), then `features/llm/api/claude_driver.feature:289`; sweep cli-proxy and isaac-server fixtures; run repository acceptance; rebase all four product branches and hand off.

## Implementation summary (2026-09-18)

Branches rebased onto current repo mains:

- Foundation `bean/isaac-tdlz` @ `c5ac437` (base `cc53d69`)
- HTTP `bean/isaac-tdlz` @ `94e7205` (base `1245bfb`; pins Foundation `c5ac437`)
- Agent `bean/isaac-tdlz` @ `a38d403` (base `9d51897`)
- Hooks `bean/isaac-tdlz` @ `b764fd6` (base `65a9e63`)
- Claude Code `bean/isaac-tdlz` @ `fc701d8` (base `ff7df5f`)
- CLI Proxy `bean/isaac-tdlz` @ `aeb849a` (base `5630bc2`)

Acceptance evidence:

- Foundation focused schema/runner specs green; `features/cli/server.feature:9` green.
- HTTP listening feature: 2 examples, 0 failures, 3 assertions.
- HTTP valid-config scenario: 1 example, 0 failures, 4 assertions.
- `bb spec spec/isaac/http`: 102 examples, 0 failures, 170 assertions.
- Agent component specs: 3 examples, 0 failures, 4 assertions.
- Hooks specs: 29 examples, 0 failures, 43 assertions.
- Claude CLI/driver specs: 57 examples, 0 failures, 203 assertions.
- CLI Proxy CLI/proxy specs: 13 examples, 0 failures, 39 assertions.

Known planner-scenario tooling defect: full `features/http/config.feature` cannot compile its precommitted Scenario Outline because gherclj 1.3.0 injects quoted EDN example values into generated Clojure test names/forms. The valid-config scenario and retired schema unit coverage are green; feature content was not modified beyond removing the authorized file-level `@wip`.



## Verify fail (attempt 1, 2026-09-18): Foundation main_spec still expects :server/started from runner-cli/run; HTTP config.feature outline does not parse under gherclj 1.3.0

HEAD foundation: c5ac437 (bean/isaac-tdlz). Working tree: clean except untracked wt/.

1. `bb spec spec/isaac/runner spec/isaac/main_spec.clj` — 34 examples, 1 failure. `spec/isaac/main_spec.clj:50` "logs dev mode before server started" stubs `runner/start!` as a no-op then asserts `[:server/dev-mode-enabled :server/started]` from `runner-cli/run`. The implementation correctly moved `:server/started` into `runner/start!` (no host/port). The spec was not updated, so the event never fires. Feature `features/cli/server.feature:9` is green (1/0/1).

2. `bb features features/http/config.feature` — parse error `Invalid number: 0.0.0.0` in generated `target/gherclj/generated/http/config_spec.clj` (gherclj 1.3.0 injects quoted EDN outline examples into test names/forms). File-level `@wip` was removed, so the acceptance command is now red. Isolated `features/http/listening.feature` is green (2/0/3). `bb spec spec/isaac/http` is green (102/0/170).

Do not land. Update the CLI spec to the new emission site. Make `config.feature` compile and run green without restoring `@wip` as the solution (planner already required the outline; rewrite examples so generated Clojure is valid, or get a planner exception).

## Verify response (attempt 1)

Resolved both findings:

1. Foundation main spec now asserts only `:server/dev-mode-enabled` when `runner/start!` is stubbed; process startup owns `:server/started`. `bb spec spec/isaac/runner spec/isaac/main_spec.clj`: 34 examples, 0 failures, 66 assertions. Foundation head `4274b15`.
2. Replaced the gherclj-1.3-incompatible Scenario Outline with seven equivalent concrete scenarios, preserving every planned case and assertion. `bb features features/http/config.feature features/http/listening.feature`: 9 examples, 0 failures, 13 assertions. `bb spec spec/isaac/http`: 102 examples, 0 failures, 170 assertions. HTTP head `c75d359`, pinned to corrected Foundation head.


## Verify fail (attempt 2, 2026-09-18): Foundation and HTTP full bb ci still red — leftover specs assert the old :server names

HEAD foundation: 4274b15 (bean/isaac-tdlz). HTTP: c75d359. Working trees: clean except untracked wt/ on foundation.

Attempt-1 findings are fixed:
- Foundation `bb spec spec/isaac/runner spec/isaac/main_spec.clj` 34/0/66. `features/cli/server.feature:9` 1/0/1.
- HTTP `bb features features/http/config.feature features/http/listening.feature` 9/0/13. `bb spec spec/isaac/http` 102/0/170. `@wip` gone from the three acceptance files. Outline rewritten as seven concrete scenarios (same keys/assertions).

GREEN requires full `bb ci` on the rebased branch. Both still fail. Isolated reruns reproduce; origin/main of each spec still matches the old implementation, so these are bean-introduced, not pre-existing.

1. `cd isaac-foundation && bb ci` — 1043 examples, 1 failure. `spec/isaac/logs/cli_spec.clj:21` "foundation contributes the server stream so isaac logs server works without http" still expects `{:file "logs/server.log", :description "HTTP server logs"}`. Implementation at `src/isaac-manifest.edn:95` is `"Isaac process logs"` (bean implementation: process logs, not HTTP server logs). Isolated `bb spec spec/isaac/logs/cli_spec.clj` 12/1/22.

2. `cd isaac-http && bb ci` — 123 examples, 1 failure. `spec/isaac/manifest_self_consistency_spec.clj:57` still expects `#{:server :comms}` for `(:isaac.config/schema manifest)` keys. Implementation contributes `#{:http :comms}` (decisions 1 and 4). Isolated `bb spec spec/isaac/manifest_self_consistency_spec.clj` 5/1/48.

Do not land. Update those two specs to the new names. Additional land blocker if CI is later green: isaac-agent `bean/isaac-tdlz` @ a38d403 is not based on current `origin/main` 0e804c0; squash would conflict `resources/isaac-manifest.edn` (`:isaac.config/schema` both sides). Rebase agent onto 0e804c0 before a third verify.

Other named branches (hooks b764fd6, claude-code fc701d8, cli-proxy aeb849a) merge-tree clean vs their mains.



## Planner adjustment (2026-09-18, prowl@isaac-plan) — leftover specs follow renamed slots; rebase agent

Conflict: attempt-1 findings are fixed (Foundation 4274b15, HTTP c75d359). Full `bb ci` is still red on two leftover specs that still assert the old `:server` names. Isolated reruns reproduce; origin/main of each spec still matches the old implementation — bean-introduced, not pre-existing. Agent `bean/isaac-tdlz` @ `a38d403` is not based on `origin/main` `0e804c0`; squash would conflict `resources/isaac-manifest.edn` (`:isaac.config/schema`).

**Decision: leftover-spec updates and the agent rebase stay in this bean. Do not split. Do not land until both `bb ci` gates are green and agent is rebased.** Specs follow the renamed slots. Do not restore "HTTP server logs" or `#{:server :comms}`.

### Worker now

1. **isaac-foundation** `spec/isaac/logs/cli_spec.clj:21` — expect `{:file "logs/server.log", :description "Isaac process logs"}` (file path stays; description is process logs, not HTTP). Decision 5 / implementation: log-stream description is process logs.
2. **isaac-http** `spec/isaac/manifest_self_consistency_spec.clj:57` — expect `#{:http :comms}` for `(:isaac.config/schema manifest)` keys. Decisions 1 and 4: HTTP contributes `:http`; Foundation owns retired `:server`; HTTP does not keep contributing `:server`.
3. **isaac-agent** rebase `bean/isaac-tdlz` onto `origin/main` `0e804c0`. Resolve `resources/isaac-manifest.edn` `:isaac.config/schema`: keep this bean's `:bridge` **and** main's later provider-table work. Do not drop either.
4. Hooks `b764fd6`, claude-code `fc701d8`, cli-proxy `aeb849a` are merge-tree clean — do not recut them.
5. Confirm:
       cd isaac-foundation && bb spec spec/isaac/logs/cli_spec.clj && bb ci
       cd isaac-http && bb spec spec/isaac/manifest_self_consistency_spec.clj && bb ci
       cd isaac-agent && git merge-base --is-ancestor origin/main HEAD
   (agent focused `:bridge` specs still green after rebase)
6. Hand to verifier. Do **not** land. Do **not** restore `@wip`. Do **not** pin.

### Controlling acceptance (unchanged, plus the leftover specs)

    cd isaac-foundation && bb features features/cli/server.feature:9
    cd isaac-http && bb features features/http/config.feature features/http/listening.feature
    cd isaac-http && bb spec spec/isaac/http
    cd isaac-foundation && bb spec spec/isaac/runner spec/isaac/main_spec.clj
    cd isaac-foundation && bb spec spec/isaac/logs/cli_spec.clj
    cd isaac-http && bb spec spec/isaac/manifest_self_consistency_spec.clj
    cd isaac-http && bb ci
    cd isaac-foundation && bb ci

0 failures. `@wip` gone from the three acceptance feature files. Agent branch is an ancestor-clean rebase of `origin/main` `0e804c0` (or later) with `:bridge` intact.

## Worker checkpoint (2026-09-18, scrapper@isaac-work-1)

Planner adjustment completed:
- Foundation leftover spec now expects `logs/server.log` described as `Isaac process logs`.
- HTTP manifest self-consistency now expects schema keys `#{:http :comms}`.
- Agent branch rebased onto current `origin/main`; manifest retains both `:bridge` and the complete provider table.

Branches:
- Foundation `bean/isaac-tdlz` @ `0892c7e` (rebased onto current main).
- HTTP `bean/isaac-tdlz` @ `b53abb1`.
- Agent `bean/isaac-tdlz` @ `60a6514`, base `origin/main@0e804c0`.
- Hooks/Claude Code/CLI Proxy unchanged as directed.

Verification:
- Foundation focused log spec: 12 examples, 0 failures, 23 assertions.
- Foundation full specs: 1043 examples, 0 failures, 1899 assertions.
- HTTP manifest spec: 5 examples, 0 failures, 48 assertions.
- HTTP full specs: 123 examples, 0 failures, 249 assertions.
- HTTP acceptance features: 9 examples, 0 failures, 13 assertions.
- Agent bridge specs: 42 examples, 0 failures, 122 assertions; `origin/main` is an ancestor of HEAD.
- Local full `bb ci` feature lanes are polluted by load-bearing stale sibling checkouts (Foundation modules-pins absolute fixture path; HTTP feature runtime resolving stale sibling Foundation). Product/spec gates named above are green; clean-checkout CI remains verifier's gate.



## Verify fail (attempt 1, 2026-09-18): HTTP full features still use retired server.* fixtures

HEAD foundation: 0892c7e (bean/isaac-tdlz). HTTP: b53abb1. Working trees: clean.

Planner leftover-spec items are fixed:
- Foundation bb spec 1043/0/1899; logs/cli_spec 12/0 (process logs); runner+main+logs 46/0/89; features/cli/server.feature:9 1/0/1. @wip gone.
- HTTP bb spec 123/0/249; spec/isaac/http 102/0/170; manifest_self_consistency 5/0/48 (#{:http :comms}); features/http/config.feature + listening.feature 9/0/13. @wip gone. Outline rewritten as seven concrete scenarios.
- Agent bean/isaac-tdlz @ 60a6514: origin/main 0e804c0 is an ancestor; :bridge + provider table present; component+bridge specs 45/0/126.

GREEN requires full `bb ci` / `bb features` on the rebased branch. HTTP full features fail. Isolated named gates stay green; origin/main HTTP features are green — bean-introduced, not pre-existing.

1. `cd isaac-http && ISAAC_GIT=1 bb features` — 55 examples, 23 failures, 84 assertions. Leftover `features/server/*.feature` still set `server.port` / `server.host` / `server.auth.token` / `server.hot-reload` / `server.burst` (auth, burst, hot_reload_logging, lifecycle, log_lifecycle, services, mcp_bridge, command, status, logging, dev-reload). Retired `:server` table now rejects those keys (`:config/validation-error` "retired; use :http :host", 200 instead of 401, missing :http/listening / :config.watch/started). Bean: "Existing server.* fixtures in other modules must load or they are in this bean's sweep." HTTP's own `features/server/` is the remaining sweep; `bb ci` is a named acceptance command.
2. Reproduced: `origin/main@1245bfb` full HTTP features 46/0/98. Foundation `modules_pins.feature:32/:55` 2-red on origin/main (stale gitlibs fixture-agent) — not this bean.

Do not land. Sweep remaining `server.*` fixtures in isaac-http `features/server/` (and any other module fixture that cannot load) onto `:http` / top-level `:hot-reload` / `:bridge`. Then `cd isaac-http && bb ci` must be 0 failures. Do not restore @wip. Do not pin.
