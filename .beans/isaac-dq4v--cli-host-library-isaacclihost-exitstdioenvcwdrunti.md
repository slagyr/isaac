---
# isaac-dq4v
title: 'CLI host library: isaac.cli.host (exit/stdio/env/cwd/runtime seam) + run-embedded + lint'
status: completed
type: feature
priority: high
tags:
    - foundation
    - cli
created_at: 2026-09-17T15:55:24Z
updated_at: 2026-09-17T16:47:03Z
parent: isaac-eqkb
---

Child 1 of isaac-eqkb. **No behavior change** — pure seam introduction.

## Problem

Commands talk to the JVM/process directly (stdin, env, cwd, tty, exit, shutdown hooks, blocking forever). Nothing abstracts it (`isaac.cli.common` is printing + arg parsing only), so commands can't be hosted anywhere but a throwaway process.

## Design — `isaac.cli.host` (foundation)

One dynamically-bound host (`*host*`), two implementations. Commands call the host; nothing else.

| call | process host (today's behavior) | embedded host |
|---|---|---|
| `(host/exit! code)` | `System/exit` | throws `ex-info {:isaac.cli/exit code}`; the embedder catches → exit code |
| `(host/ensure-runtime! opts)` | load config, install session store / tools into nexus (what commands hand-roll today) | no-op; asserts the live runtime is present |
| `(host/in)` `(host/out)` `(host/err)` | real stdio | streams supplied by the embedder; `run-embedded` also binds `*in*`/`*out*`/`*err*` so bare `println`/`read-line` work |
| `(host/cwd)` `(host/env k)` `(host/tty?)` | `user.dir`, `System/getenv`, `System/console` | values supplied by the embedder (cwd = server root; env/tty from the handshake) |
| `(host/on-shutdown! f)` | JVM shutdown hook | registered on the stream; run on cancel |
| `(host/block-until-cancelled!)` / `(host/cancelled?)` | `@(promise)` / false | parks until the embedder cancels / true after cancel |

- `isaac.main/-main` becomes the ONLY caller of the process host's real `System/exit`.
- `(host/run-embedded {:argv … :in … :out … :err … :env … :tty? …})` → exit code. It resolves the command from the LIVE registry and invokes its run-fn against the LIVE nexus with `root/*root*` bound. It MUST NOT call `isaac.main/run` (no `clear-process-memo!`, `clear-berth-commands!`, `reconcile-modules!`, `apply-cli!`, `nexus/init!`, no `with-redefs`). Handles `--help`/`--version`/unknown-command/aliases itself. `--root` ≠ server root ⇒ exit 2 with a clear message. Uncaught throwable ⇒ message on err, exit 1.
- Manifest `:isaac/cli` entries gain optional `:local-only true`; `run-embedded` refuses those (exit 2, "run this on the host"). Mark `server`, `service`, `modules` (install/upgrade subcommands at minimum) here.
- Migrate foundation: `color/tty?` + `color/env` → host; `config` stdin (`validate.clj:52,59`, `mutate_common.clj:40`, `common.clj:183` read-line) → host in; `logs --follow` loop checks `host/cancelled?`; `runner/cli.clj` shutdown hook + `block!` → host; `user.dir` reads in command code → `host/cwd`.
- Replace the `with-redefs [log/log* …]` in `main/register-module-cli-commands!` with a binding-based quiet flag (thread-safe; it is wrong even in a plain process with futures).
- **Lint** (`bb lint-cli-host`, wired into `bb ci`; reusable by module repos): fails on `System/exit`, `System/getenv`, `System/console`, `System/setProperty`, `"user.dir"`, `addShutdownHook` under `src/` outside an allowlist (`isaac/cli/host.clj`, `isaac/main.clj`, launcher/bootstrap namespaces). A lint task, NOT a scenario.

## Acceptance

Specs (isaac-foundation `spec/isaac/cli/host_spec.clj`):
1. embedded `exit!` returns the code from `run-embedded` and the JVM survives; code after `exit!` in the command does not run.
2. `run-embedded` of a command that prints on a `future` captures that output on the supplied out (binding conveyance).
3. stdin supplied by the embedder is readable via `read-line`/`slurp *in*` inside the command.
4. `run-embedded` leaves these untouched (identity/values before == after): registry `commands` atom, logger state, `config-api` process memo, nexus root-runtime, activated modules.
5. `:local-only` command → exit 2 + message; mismatched `--root` → exit 2; throwing command → exit 1, message on err.
6. `block-until-cancelled!` returns when the embedder cancels; `on-shutdown!` fns run once.
7. process host: `-main` still exits nonzero via the real exit (existing CLI features stay green).

```
cd isaac-foundation && bb spec spec/isaac/cli && bb features features/cli && bb lint-cli-host && bb ci
```

## Likely repo scope

isaac-foundation only.

## Worker checkpoint (2026-09-17, scrapper@isaac-work-1)

Done:
- Implemented `isaac.cli.host` with process and embedded hosts, live-registry `run-embedded`, embedded exit/stdio/env/cwd/tty/cancellation behavior, local-only/root/error handling, and binding conveyance coverage.
- Migrated foundation command process access, config stdin, color detection, runner shutdown/blocking, and log-follow cancellation through the host seam.
- Replaced discovery `with-redefs` logging suppression with binding-based `logger/*quiet?*`.
- Added optional manifest `:local-only`, marked server/service/modules, added reusable `lint-cli-host`, wired it into `bb ci`, and added the namespace to the foundation boundary.
- Branch: `bean/isaac-dq4v` @ `43440478b386a8d5ac758408f12bf58d32fa4aca` (base `origin/main@f9ae3fd97b4cd882a624493ef3ca74940c96d856`).

Verification:
- `bb spec spec/isaac/cli` — 44 examples, 0 failures, 90 assertions.
- `bb spec` — 1038 examples, 0 failures, 1888 assertions.
- `bb lint-cli-host` — ok.
- `bb lint` — 0 errors (pre-existing warnings).
- `bb features features/cli` and local `bb ci` reach the known local fixture-agent cache failure in two modules-pins scenarios; unrelated to this branch and GitHub CI has a valid fixture.

Next:
- Verifier starts at `src/isaac/cli/host.clj:7` and `spec/isaac/cli/host_spec.clj:21`, runs acceptance in a clean checkout/CI, and reviews whether modules may later narrow `:local-only` to install/upgrade subcommands.



## Landed on main (2026-09-17)

main-sha: isaac-foundation 26742d0d67179412434de3f1f13d8103a15bd173
