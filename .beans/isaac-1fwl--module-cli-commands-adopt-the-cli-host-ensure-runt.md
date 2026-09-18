---
# isaac-1fwl
title: 'Foundation CLI host: ensure-runtime! installer memoization + :hosted passthrough'
status: completed
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-17T15:55:24Z
updated_at: 2026-09-18T01:51:51Z
parent: isaac-eqkb
---

Child 2 of isaac-eqkb. Blocked by the host-library bean. Behavior in a plain process is unchanged; commands become SAFE to embed.

## Work, per repo (each adopts foundation's `bb lint-cli-host` in its `bb ci`)

| repo | command(s) | change |
|---|---|---|
| isaac-agent | `sessions` | `install-cli!` (session/cli.clj:238) → `host/ensure-runtime!`; **delete `config/dangerously-install-config! nil` in the `finally`s (:277, :454)** — process host can do its own teardown; `builtin/register-all!` (:269) moves behind ensure-runtime! |
| isaac-agent | `prompt` | `install-config!` (prompt_cli.clj:130), `runtime/install!` (:311), `builtin/register-all!` (:252) → ensure-runtime!; `user.dir` (:163,:202) → `host/cwd` |
| isaac-agent | `auth`, `crew`, `turns` | `load-config!` → ensure-runtime!; `auth` read-line via host in; device-code poll loop checks `host/cancelled?` |
| isaac-acp | `acp` | `set-snapshot!` (cli.clj:73), `nexus/register!`/`store/register!` (:171-172), `register-all!` (:209) → ensure-runtime!; stdin loop (:141) via host in; **remove `System/setProperty "user.dir"` (server.clj:32-47)** — pass cwd explicitly to `open-acp-session!`; **replace `--verbose` `with-redefs` (cli.clj:148)** with a binding/tap |
| isaac-hail | `hail` | stdin (cli.clj:62) via host in |
| isaac-episodes | `embed`, `recall`, `episodes` | `load-config!`/`runtime/install!` → ensure-runtime! |
| isaac-claude-code | `mcp-bridge` | stdin loop (cli.clj:113) + `ISAAC_SERVER_TOKEN` env via host |
| isaac-worksite | `worksites` | lock owner must not be bare PID (lock.clj:19-28): stamp an owner id (`pid` + stream/invocation id) so an embedded operator lock is distinguishable from the server's own turn locks; liveness = owner still registered, falling back to pid-alive for foreign pids |
| isaac-cli-proxy | `remote` | mark `:local-only true`; tty/stdin via host |
| isaac-foreman | `foreman` | lint only |

Also fix: isaac-server `http/cli.clj:119` defines `cli-api/run :server`, shadowing foundation's by load order — resolve to one owner (isaac-3q4m says foundation).

## Acceptance

Per repo, a spec that runs each command via `host/run-embedded` with a live-runtime fixture and asserts (a) correct output/exit, (b) the ambient config snapshot, nexus `:sessions :store`, and tool registry are IDENTICAL objects before and after (the `sessions` nil-out is the named regression), (c) stdin-driven subcommands read from the supplied stream. Worksite: spec that an embedded lock and a server turn lock in the same pid do not read as the same owner.

```
for r in isaac-agent isaac-acp isaac-hail isaac-episodes isaac-claude-code isaac-worksite isaac-cli-proxy isaac-foreman; do (cd $r && bb lint-cli-host && bb ci); done
```

Planner note: split per repo at dispatch time if a worker wants smaller units (agent / acp / rest is the natural 3-way cut). Draft until the fixture shape from child 1 is known.

Each migrated command sets `:hosted true` on its `:isaac/cli` manifest entry — the transitional marker child 3 reads to choose embedded vs subprocess; child 5 deletes it.



## Landed upstream (isaac-dq4v, foundation main 26742d0) — what this bean builds on
`isaac.cli.host`: `*host*` (ProcessHost default), `exit!`, `ensure-runtime!`, `in/out/err`, `cwd`, `env`, `tty?`, `on-shutdown!`, `block-until-cancelled!`, `cancelled?`, `cancel!`, `embedded-host`, `run-embedded`. Foundation's `bb lint-cli-host` (`isaac.foundation.cli-host-lint/lint!`, takes paths) is reusable from module `bb.edn`s. Manifest `:isaac/cli` entries accept `:local-only`.

**Foundation leg (do first, bump pins):** `ProcessHost/-ensure-runtime!` is a no-op today, so a module command has nowhere to put its process-side bootstrap. Extend the contract: `(host/ensure-runtime! {:install! (fn [] …)})` — the process host calls `:install!` once per process (memoized on identity, so several commands/subcommands in one invocation don't re-install); the embedded host ignores `:install!` and only asserts the live nexus (as landed). Commands pass their existing config-load + store/tool registration as `:install!`. This is how "no ambient installs when embedded" is achieved without every module growing an if.

Each migrated command sets `:hosted true` on its `:isaac/cli` manifest entry (isaac-qvhy reads it; isaac-dqy9 deletes it). The registry passthrough for `:hosted` lands in isaac-qvhy's foundation leg — coordinate on the pin; if this bean lands first, add the passthrough here instead (one line at `registry.clj:229-236`).

## Fixture for the per-repo spec
`(host/run-embedded {:argv [...] :in (StringReader. "...") :out (StringWriter.) :err (StringWriter.) :root <root> :env {} :cwd <root>})` on a thread whose nexus is the live one (the spec installs a real config + store via the module's own `:install!` first, then snapshots the ambient config object, `(nexus/get-in [:sessions :store])`, and the tool registry, runs the command, and asserts identical objects after). `sessions` must be run through this — the `finally` nil-out is the named regression.

## Work checkpoint (2026-09-17, scrapper@isaac-work-3)

Completed and pushed the prerequisite Foundation leg on `bean/isaac-1fwl` @ `f81f5ef` (base `origin/main@f16efcf`): process-host `ensure-runtime!` invokes each installer identity once, retries failed installers, embedded hosts continue to reject missing live runtime without invoking installers, and manifest CLI registration retains the transitional `:hosted` marker. Foundation host specs (9 examples/26 assertions), hosted-marker spec, and `bb lint-cli-host src spec` pass.

Remaining scope is the eight module migrations plus server duplicate-command cleanup. Current foundation commit must land first and module pins must then be advanced before module code can compile against the extended contract. Repo inventory/checkouts exist for agent, acp, hail, episodes, claude-code, worksite, cli-proxy, foreman, and server.



## Planner adjustment (2026-09-18, prowl@isaac-plan) — Foundation-only; split remaining modules

Conflict: this bean spanned Foundation plus eight module repos and server cleanup — an unverifiable nine-repo mega-handoff. The bean already permitted a dispatch-time 3-way cut (agent / acp / rest). Worker completed the Foundation prerequisite on `bean/isaac-1fwl` @ `f81f5ef`.

**Decision: this bean is now Foundation-only.** Module migrations are split. Do not migrate agent/acp/hail/episodes/claude-code/worksite/cli-proxy/foreman/server on this bean.

### Split (draft — human promote to todo)

| bean | repo(s) | status |
|---|---|---|
| **isaac-1fwl** (this) | isaac-foundation | in-progress — land `f81f5ef` |
| **isaac-kk0o** | isaac-agent (`sessions`, `prompt`, `auth`, `crew`, `turns`) | draft, blocked-by 1fwl |
| **isaac-ow5u** | isaac-acp (`acp`) | draft, blocked-by 1fwl |
| **isaac-x2lp** | hail, episodes, claude-code, worksite, cli-proxy, foreman, server | draft, blocked-by 1fwl |

isaac-dqy9 (end cap) now blocked-by **kk0o + ow5u + x2lp** (plus existing qvhy / gar0 / kjzq), not this bean.

### Landing / pin order

1. Land isaac-foundation `bean/isaac-1fwl` @ `f81f5ef` on main (verifier squash). Record `main-sha`.
2. **Planner** pins `modules.edn` to that SHA. Human `modules upgrade`. Do not pin from verify.
3. Then kk0o / ow5u / x2lp may compile (each bumps its own foundation pin after the registry pin). They may run in parallel.
4. qvhy stays independent (transitional subprocess for un-hosted commands).

### Controlling acceptance (this bean, Foundation only)

isaac-foundation `bean/isaac-1fwl` @ `f81f5ef` (or rebased / squash equivalent):

    bb lint-cli-host src spec
    bb spec

0 failures. `ProcessHost/-ensure-runtime!` calls each installer identity once (memoized), retries a failed installer, embedded host still rejects a missing live runtime without invoking installers. Manifest CLI registration retains `:hosted`.

Do **not** require module `bb ci` on this bean. Do **not** migrate module commands here.

Worker now: do not start module work on this bean. Hand Foundation to verifier. Completing this bean unblocks the three drafts (after human promotion).

## Worker checkpoint (2026-09-18, scrapper@isaac-work-1)

Foundation-only scope complete per planner adjustment:
- Rebased `bean/isaac-1fwl` onto current Foundation main.
- Branch: `bean/isaac-1fwl` @ `b65f6c6cd802a2b030923947e6b3b2265791f3cc` (base `origin/main@0be8d3140b53b05c8181866c6992feef34686bc6`).
- ProcessHost installer memoizes successful installer identities and retries failed installers.
- Embedded host behavior remains unchanged; manifest command registration retains `:hosted`.
- No module repos were modified in this resumed turn.

Verification:
- `bb lint-cli-host src spec` — ok.
- `bb spec` — 1041 examples, 0 failures, 1891 assertions.
- `git diff --check` — clean.



## Landed on main (2026-09-18)

main-sha: isaac-foundation cc53d69a50bdf38f6f5b2b76adcde3f8fa8142f8
