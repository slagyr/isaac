---
# isaac-dqy9
title: Embed prompt + acp; delete subprocess spawning from cli-server (end cap)
status: in-progress
type: feature
priority: normal
tags:
    - cli
created_at: 2026-09-17T15:55:25Z
updated_at: 2026-09-21T02:16:01Z
parent: isaac-eqkb
blocked_by:
    - isaac-qvhy
    - isaac-gar0
    - isaac-kjzq
    - isaac-kk0o
    - isaac-ow5u
    - isaac-x2lp
    - isaac-kjzq
    - isaac-kk0o
    - isaac-ow5u
    - isaac-x2lp
---

Child 5 of isaac-eqkb (end cap). Blocked by children 2, 3, 4.

## Work

- `prompt` and `acp` run embedded: their sessions are created/written through the server's live store under the JVM persist lock (isaac-4zr3) — the single-writer goal. Embedded `prompt` goes through the same turn gate as every other turn (honors `:max-in-flight`; one turn per session).
- `acp` over the pipe: long-lived duplex on a hosted stream; editor closes stdin → acp returns → exit frame. Server restart drops the stream; proxy reattach gets unknown-stream-id and exits; editor reconnects (accepted, see epic).
- **Delete subprocess spawning from isaac-cli-server**: `babashka.process`, `*spawn-process*`, `*launcher-command*`, `spawn-options`, the `:hosted` transitional marker (child 3), and the spawn-stub feature steps. One-time acceptance criterion (not a permanent scenario): `grep -rn "babashka.process\|spawn-process" isaac-cli-server/src` is empty.
- PROTOCOL.md final wording, both repos.

## Acceptance (draft — scenarios at promotion)

- isaac-cli-proxy `features/integration.feature` remote-ACP e2e (isaac-lcay's scenario) green against embedded acp.
- a `prompt` sent over the pipe while the same session has a turn in flight queues/refuses per the turn gate instead of writing concurrently (spec: two writers, every transcript line parses — jz6h's assertion, now across the CLI boundary).
- zanebot soak: a day of crew traffic with zero `:session/transcript-torn` / `:session/unreadable`.



## Scenarios (committed @wip — isaac-cli-proxy `features/integration.feature` @ 3bc4564, @slow lane)

| line | scenario |
|------|----------|
| :56 | a remote ACP session runs inside the server process |
| :74 | a remote prompt runs inside the server process and its turn is visible to the server |

Both assert `:cli/command-started … :hosted true` in the REAL server's log — cli-server adds `:hosted` to that log entry (true for embedded, false for the transitional subprocess path while it still exists). Note the lcay scenario passes `--root ${fixture.root}`; embedded dispatch refuses a `--root` that is not the server's root, so the fixture server must be started on that root (or the arg dropped, as in :56).

Specs (isaac-cli-server or isaac-agent, worker's call):
1. a second `prompt` over the pipe against a session with a turn in flight is gated by the turn gate (queues/refuses per `:max-in-flight` / one-turn-per-session), and every transcript line parses afterwards (jz6h's assertion across the CLI boundary).
2. embedded `acp` cancellation (grace expiry) closes the session cleanly — no partial record.

## One-time acceptance (not a permanent scenario)
`grep -rn "babashka.process\|spawn-process\|launcher-command\|:hosted" isaac-cli-server/src` is empty after the subprocess path and the transitional marker are deleted; the two isaac-895i subprocess scenarios (`cat`, `sh -c 'exit 3'`) and the `:213` transitional scenario are removed from endpoint.feature, and the recording-spawn-stub steps go with them.

## Step ledger

| step | status |
|------|--------|
| a real cli-server backed by an isaac install with an echo model / isaac remote is run interactively with … / the ACP client steps / the client closes stdin / the exit code is … / stdin is empty / isaac is run with … / the stdout contains … | reuse |
| **the server log has entries matching:** | **NEW — reads the fixture server's log (same table shape as cli-server's `the cli log has entries matching:`)** |

One new step.

## Acceptance
```
cd isaac-cli-proxy && bb features-slow features/integration.feature && bb ci
cd isaac-cli-server && bb features && bb spec && bb ci
```
zanebot soak after the train: a day of crew traffic with zero `:session/transcript-torn` / `:session/unreadable`. PROTOCOL.md final wording, both repos.

Dispatched: hail d03cbc71 2026-09-21T01:48:02Z (band isaac-work, pinned session isaac-work-1 on model tono-opus / provider tono-claude — second Claude subscription trial, isaac-12fo)

## Worker checkpoint — two planner decisions needed (2026-09-20, scrapper@isaac-work-1)

Branches pushed, both suites green except the one pre-existing red called out below:

- isaac-cli-server `bean/isaac-dqy9` @ `1d630a3` — `bb ci` green (15 spec / 0 failures, 18 scenarios / 0 failures, config-bypass-lint ok)
- isaac-cli-proxy  `bean/isaac-dqy9` @ `2c94520` — `bb spec` 24/0, `bb features` 29/0, `bb features-slow features/integration.feature` 5 examples / 1 failure (pre-existing, see C below), config-bypass-lint + lint-cli-host ok

### Done

- **Subprocess spawning deleted from isaac-cli-server.** `babashka.process`,
  `*spawn-process*`, `*launcher-command*`, `spawn-options`, `launcher-command`,
  `start-process!`, `hosted-command?`, `stream-frames!`, `await-exit!`,
  `start-streaming!` are gone; `start-stream!` always runs `start-hosted!`.
  `grep -rn "babashka.process\|spawn-process\|launcher-command" src` is empty.
  `babashka/process` also dropped from the `:test` alias in `deps.edn`.
- **The `:hosted` marker is no longer consulted.** Every command embeds; only
  `:local-only` opts out, refused over the pipe with `run this on the host` /
  exit 2. Commands that were never marked hosted now embed too — covered by a
  new spec, "runs a command that carries no hosted marker on an embedded task".
- **`acp` and `prompt` embed with no further code change** — that falls out of
  the marker removal. The e2e proves acp: the new scenario
  `a remote ACP session runs inside the server process (isaac-dqy9)` is
  un-`@wip` and **passes**, asserting `:cli/command-started … hosted true` in the
  real server's log.
- **Real defect found and fixed by that e2e** (isaac-cli-server `1d630a3`):
  embedded stdin used `java.io.Piped{Input,Output}Stream`, which binds to the
  thread that last wrote it and throws `Write end dead` once that thread exits.
  Every `stdin` frame arrives on a different http-kit worker, so an interactive
  embedded command died after its first frame — ACP got its `initialize`
  response and then `Invalid Request` + EOF. Replaced with a
  `LinkedBlockingQueue`-backed `Reader` (no thread affinity). The in-JVM
  fixtures never caught this because the test thread does all the writing.
- **endpoint.feature**: every subprocess scenario removed — the two isaac-895i
  ones (`cat`, `sh -c 'exit 3'`), the isaac-895i launcher-recording one, the
  isaac-4tn1 subprocess grace window, the isaac-iouj spawn-stub logging one, the
  five `spawn command` batch scenarios, and the `:213` transitional scenario —
  together with the recording-spawn-stub steps. Each is superseded one-for-one
  by the isaac-qvhy embedded scenarios (duplex streaming, containment, grace
  window, reattach, logging) or by isaac-cli-proxy `features/integration.feature`
  (`--version` over the pipe end to end). A comment block in the feature records
  the mapping. `dispatch_spec.clj` rewritten the same way: the spawn examples
  became embedded ones, nothing was dropped without a replacement.
- **PROTOCOL.md final wording, both repos**, byte-identical as the file requires:
  "Execution model (server)" now describes embedded dispatch, the single-writer
  reason, `:local-only`, `--root`, the `hosted true` log key, exit 124, and
  server-restart stream loss.

### A. The bean contradicts itself on `:hosted`

The Work section and the One-time acceptance ask for `grep -rn "…\|:hosted"
isaac-cli-server/src` to be **empty**. The two committed scenarios require the
opposite: both assert `:cli/command-started … hosted true` in the server log, so
`src/isaac/cli_server/dispatch.clj` must emit the key. It does, once, at
`log-command-started!` — the only `:hosted` left in `src`.

I resolved it in favour of the scenarios (permanent contract beats a one-time
grep) and did not touch the scenarios. **Please ratify or re-cut the grep.**

Note the *registry* marker is a separate thing and is untouched: `:hosted` is
still declared in `isaac-foundation` `src/isaac-manifest.edn` + `cli/registry.clj`
and in `isaac-acp`'s manifest. It is now inert — nothing reads it. Removing it
for real means editing foundation's config schema and every module manifest that
sets it, which is well outside this bean's two repos. Worth its own bean.

### B. The `prompt` scenario cannot pass as written — `${server.url}` is not interpolated

`a remote prompt runs inside the server process…` runs

    When isaac is run with "remote ${server.url} -- prompt --crew main --session dqy9-e2e -m ping"

but the shared `isaac is run with` step interpolates **only** `${server.port}`:

    isaac-foundation spec-support/src/isaac/foundation/cli_steps.clj:78-81
    (defn- interpolate-args [args]
      (cond-> args
              (g/get :server-port) (str/replace "${server.port}" (str (g/get :server-port)))
              true                 (str/replace "\\\"" "\"")))

So the proxy is handed the literal string `${server.url}` and cannot connect.
`${server.url}` works in the ACP scenarios only because those use
`isaac remote is run interactively with`, a step this repo owns and interpolates
itself.

I re-applied `@wip` to that one scenario so the branch is not left red on a
harness gap; the server-side work it exercises is already done and proven by the
ACP scenario next to it. Three ways out, all yours:

1. Add the symmetric line to foundation's `interpolate-args`
   (`(g/get :server-url) (str/replace "${server.url}" …)`). One line, but it
   makes isaac-foundation a third repo on this bean, with a landing order and a
   repin in both cli-server and cli-proxy. The bean's acceptance names only two
   repos, so I did not do it unasked.
2. Re-cut the scenario to `remote ws://localhost:${server.port}/cli` — the same
   shape the three scenarios above it already use, no foundation change.
3. Split the prompt scenario into its own bean behind the foundation change.

### C. Pre-existing red blocks the bean's stated acceptance

The bean's acceptance is `cd isaac-cli-proxy && bb features-slow … && bb ci`, but
`the server rejects a remote command without a valid token` is **already failing
on untouched `origin/main`**. Verified by running the slow lane in a detached
worktree at `5b2418a` with none of my changes: `4 examples, 1 failures`, same
scenario. My branch is at parity plus one newly passing scenario
(`5 examples, 1 failures`). Not this bean's to fix — it wants its own bug bean.
(The shared `isaac-cli-proxy` checkout also carries uncommitted work touching
`src/isaac/cli_proxy/cli.clj`, `proxy.clj` and `proxy_spec.clj`, which looks like
someone mid-chase on exactly this. I left it alone.)

### Not started (blocked behind A/B, or explicitly deferred)

- The two specs the bean lists: (1) a second `prompt` over the pipe against a
  session with a turn in flight is gated by the turn gate, every transcript line
  still parses; (2) embedded `acp` cancellation at grace expiry closes the
  session cleanly. Both want the prompt path proven first (B).
- zanebot soak (a day of crew traffic, zero `:session/transcript-torn` /
  `:session/unreadable`) — an operational step after the train lands.

### Pins — read before landing

`isaac-cli-proxy` `bb.edn` and `deps.edn` currently point isaac-cli-server at
`{:local/root "../isaac-cli-server-dqy9"}` for in-flight testing. **isaac-cli-server
must squash to `main` first**, then both files are rewritten to that landed sha
and `bb ci` re-run before isaac-cli-proxy squashes. Both files also now carry
`io.github.slagyr/isaac-acp {:git/sha "c3560df78f8c163923c8965b2c2cfe76264a6c36"}`
(isaac-acp `main`, the isaac-ow5u commit) — the server needs the acp command in
its own registry now that acp is not a subprocess; the old `738fe6b6` coord in
`integration_steps.clj` was bumped to the same sha.
