---
# isaac-gar0
title: 'Remote-by-default CLI: ~/.config/isaac.edn :cli :remote routes isaac commands through the server (--local bypass; unreachable fails)'
status: in-progress
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-17T15:55:25Z
updated_at: 2026-09-18T02:44:05Z
parent: isaac-eqkb
blocked_by:
    - isaac-qvhy
---

Child 4 of isaac-eqkb. Blocked by isaac-qvhy. **This is the bean that delivers single-writer** — the remote pipe is a minor second-writer source; the major one is local: every crew tool shell-out (`isaac …`, hundreds per bean) and every SSH'd command on zanebot is a cold process writing beside the server. Redesigned 2026-09-17 (Micah): explicit remote-by-default setting, not auto-discovery. The same-machine server is just the case where the remote is localhost.

## Decisions (2026-09-17, Micah)

- **Default stays: CLI runs as a separate local process.** Foundation knows nothing about remotes until the remote CLI module (isaac-cli-proxy) is installed AND the setting is on.
- **The setting lives in the home pointer file `~/.config/isaac.edn`** (already read raw by `isaac.config.root/pointer-value` for `:root`, before any config resolution — so the routing decision costs no config load and works on a client with no real root). Same fallback file `~/.isaac.edn`.
- **Remote unreachable ⇒ the command FAILS**, reporting the remote is not reachable and why when known. Never a silent cold fallback (from a laptop that would run against the wrong machine's root).
- **A client-side flag disables the automatic remote for one invocation.**

## Design

Pointer file shape:

```clojure
{:root "/Users/zane/.isaac"
 :cli  {:remote {:url   "wss://zanebot.example/cli"   ; ws://127.0.0.1:PORT/cli for the same-machine case
                 :token "${ISAAC_SERVER_TOKEN}"}}}     ; env ref preferred
```

- **Token**: `${VAR}` substituted from the environment at use. A LITERAL token is accepted only when the pointer file is mode 0600 — otherwise refuse with a message naming the chmod. The token is never written to `cache/cli.edn` or any log.
- **Routing (launcher, before classpath compose / config resolve)**: when `:cli :remote` is present, the proxy module is installed, the command is not `:local-only`, and `--local` was not given ⇒ behave exactly as `isaac remote <url> -- <argv>` (stdio relay, exit code = server's code, reconnect/grace semantics unchanged). Otherwise today's cold path.
- **`:local-only` commands always run locally** (`server`, `service`, `modules install|upgrade`, `remote`) — so a down server can still be started with the setting on.
- **`--local`** global flag (extracted with `--root`/`--log-file` in `isaac.cli.args/extract-root-flag`) and `ISAAC_CLI_LOCAL=1` env (for scripts/cron): force the cold local path for this invocation.
- **Managing the setting**: `isaac remote use <url> [--token-env VAR | --token TOKEN]`, `isaac remote off`, `isaac remote status` (prints the target + a reachability/auth probe). These edit the pointer file, preserve `:root` and unknown keys, and set 0600 when writing a literal token. Deliberately NOT `isaac config` — `config` routes to the remote once the setting is on, so it could never turn the setting off.
- **Failure reporting** (stderr, exit 69 EX_UNAVAILABLE; auth = 77 EX_NOPERM): connection refused / DNS failure / TLS error / connect timeout (bounded, default 3 s) / `401` at upgrade ("token rejected") / unset token env var (names the var) / proxy module not installed (names the install command). Each message ends with: "run with --local to bypass".
- **Stale basis is the SERVER's job, not the client's** (moves the old client-side basis check out of this bean): when the server's loaded module basis is behind on-disk, the embedded dispatcher refuses commands whose manifest entry is not `:read-only true` with "server restart pending" (exit 75 EX_TEMPFAIL); read-only commands still run. Add `:read-only true` hints to the obvious readers (`help`, `logs`, `sessions list|show`, `crew`, `config get`, `hail list`, `recall`). Per-subcommand granularity: `:read-only #{"list" "show"}`.

## Acceptance (draft — scenarios + step-ledger table at promotion)

1. no `:cli :remote` ⇒ the command runs locally; no connection attempted.
2. setting present ⇒ `isaac sessions list` is relayed (stub server sees the start frame with the argv); the client process performs ZERO config resolutions (isaac-v1la's resolution spy).
3. setting present, `--local` (and `ISAAC_CLI_LOCAL=1`) ⇒ runs locally.
4. setting present, `:local-only` command ⇒ runs locally.
5. unreachable remote ⇒ nonzero exit, stderr names the url and the reason (one scenario per reason: refused, 401, unset token var); NOTHING runs locally.
6. literal token in a non-0600 pointer file ⇒ refused with the chmod hint; env-ref token in a 0644 file ⇒ fine.
7. `remote use` / `remote off` / `remote status` round-trip the pointer file and preserve `:root`.
8. server with a stale basis refuses a mutator with "restart pending" and runs a `:read-only` command.
- zanebot: `/usr/bin/time -p isaac --version` and `isaac sessions list` recorded before/after enabling the setting.

## Open question

Client cost floor: the routed path must not pay isaac's load. The launcher should decide from the pointer file alone and require only the proxy namespaces (JDK WebSocket client). Measure; if requiring the proxy still drags in foundation's config stack, split a minimal `isaac.cli-proxy.client` ns.

Likely repo scope: isaac-foundation (launcher routing seam, `--local`, pointer-file reader), isaac-cli-proxy (`remote use|off|status`, failure reporting), isaac-cli-server (stale-basis refusal), `:read-only` hints across module manifests.



## Split (2026-09-17)
The server-side stale-basis refusal + `:read-only` hints moved to **isaac-kjzq** — it depends only on isaac-qvhy and can run in parallel. This bean is routing + the setting + failure reporting.

## Scenarios (committed @wip)

isaac-foundation `features/cli/remote_routing.feature` @ f16efcf (routing; a stub fills the remote-runner seam):

| scenario |
|----------|
| with no remote setting the command runs locally |
| a remote setting ships the command to the server without resolving the config |
| the remote runner's exit code becomes the local exit code |
| --local bypasses the remote for one invocation |
| ISAAC_CLI_LOCAL=1 bypasses the remote for scripts |
| a local-only command always runs locally |
| an unreachable remote fails the command with the reason and never falls back to local |
| a remote setting without the remote CLI module installed is an error naming the module |

isaac-cli-proxy `features/remote.feature` @ 9a441cd (the module's half):

| scenario |
|----------|
| remote use writes the setting and preserves the root pointer |
| remote use with a literal token writes a private file |
| remote off removes the setting and keeps everything else |
| remote status reports the target and a successful probe |
| remote status with no setting says so |
| a rejected token is reported as an auth failure (exit 77) |
| a refused connection is reported with the url and reason (exit 69) |
| a connect timeout is bounded and reported (`ISAAC_REMOTE_CONNECT_SECS`, exit 69) |

## The seam
Foundation's launcher reads the pointer file (raw, `isaac.config.root` already does for `:root`), and when `:cli :remote` is present resolves a **remote runner** — a var the remote CLI module provides (`isaac.cli-proxy.client/run!`, `(fn [{:keys [url argv]}] exit-code)`), looked up by name via `requiring-resolve` AFTER classpath compose (the module must be on the composed classpath) — and calls it instead of `isaac.main/-main`. Module absent ⇒ exit 69 naming `isaac.cli-proxy`. The runner reuses isaac-tvcg's token resolver. `--local` / `ISAAC_CLI_LOCAL` / `:local-only` short-circuit BEFORE the lookup. The runner must not drag in foundation's config stack — measure; split a minimal client ns if it does.

`remote use --token-file PATH` copies the file's contents into the pointer file as a literal and chmods it 600; `--token-env VAR` stores `${VAR}`. `remote use` never takes the token on argv.

## Step ledger

| step | status |
|------|--------|
| the user home directory is … / the file … exists with: / an empty Isaac root at … / environment variable … is … | reuse (foundation) |
| isaac is run with … / the exit code is … / the stdout|stderr contains|does not contain … | reuse |
| the config resolution spy is armed / was invoked exactly N times | reuse (isaac-v1la) |
| **a stub remote runner is installed** (+ **… that exits with code {n}** / **… that fails with reason {text}**) | **NEW — binds the remote-runner seam to a recording stub** |
| **the stub remote runner received url {url} and argv {argv}** / **the stub remote runner was not invoked** | **NEW** |
| a stub /cli server that replies with frames: / isaac remote is run with … / a file {path} with mode … / the home config file with mode {mode} contains: | reuse (proxy; the last two land with isaac-tvcg) |
| **the home config file matches:** / **the home config file has mode {mode}** / **the home config file contains {text}** | **NEW (proxy)** |
| **a stub /cli server that rejects the upgrade with 401** / **no server is listening at the stub url** / **a stub /cli server that never completes the upgrade** | **NEW (proxy)** |

## Acceptance
```
cd isaac-foundation && bb features features/cli/remote_routing.feature && bb ci
cd isaac-cli-proxy && bb features features/remote.feature && bb ci
```
zanebot after the train: `isaac remote use ws://127.0.0.1:<port>/cli --token-env ISAAC_SERVER_TOKEN` (token in the zane user's env), then `/usr/bin/time -p isaac sessions list` recorded against today's cold number; `isaac --local --version` still cold. Version bumps; pins are a train step.

## Worker checkpoint (2026-09-18, scrapper@isaac-work-2)

Done: Foundation branch `bean/isaac-gar0` @ `bb00300` adds raw pointer reading, `--local`, remote routing before config resolution, failure/module diagnostics, routing feature steps, and activates the authorized scenarios. Focused scenario `features/cli/remote_routing.feature:26` is green (1 example/3 assertions); CLI args specs are green (8/8).

Current state: remaining Foundation routing scenarios have not been rerun after the pointer/home fixture fix; proxy management/failure implementation is untouched. Next: run `bb features features/cli/remote_routing.feature`, fix any remaining red, then implement `isaac remote use|off|status` at `isaac-cli-proxy-gar0/src/isaac/cli_proxy/cli.clj:18` and drive `features/remote.feature:305` onward.
