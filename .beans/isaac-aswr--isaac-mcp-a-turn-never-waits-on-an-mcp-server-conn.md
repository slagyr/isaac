---
# isaac-aswr
title: 'isaac-mcp: a turn never waits on an MCP server — connect in the background, hold failures with backoff'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-25T02:00:22Z
updated_at: 2026-09-25T02:17:45Z
---

## Symptom

On yopp every turn waited 30 s before `drive/turn-accepted` — the 👀 reaction
and the reply both came ~50 s after the message. Four turns on 2026-09-24/25,
four `mcp/connect-failed :server :linear "MCP initialize failed: timeout"`
lines, each stamped the same second the turn was accepted. The Linear MCP
was misconfigured (header expanded empty → OAuth dance → hang), but the
runtime turned one broken server into a per-turn tax on every reply.

## Cause

`isaac.mcp.runtime/ensure-server!` is the `:isaac.agent/tool-providers`
entry. The agent's tool registry calls it while resolving a turn's tool
context (`isaac.tool.registry/tool-providers`, called for any crew whose
`:allow` names the server prefix — yopp allows `:linear/*`). When no live
client exists it calls `connect-server!` **synchronously**: spawn +
`initialize` + `tools/list`, bounded only by `client/DEFAULT-TIMEOUT-MS`
(30 s). `RETRY-HOLD-MS` (60 s, isaac-vadd) only helps when turns arrive
within a minute of the last failure; yopp's turns were minutes apart, so
every one re-paid the full timeout. `start!` (boot and every `:mcp` config
reload) has the same synchronous shape.

Micah's ruling (2026-09-25): **a turn never waits on an MCP server** — not
on spawn, not on initialize, not on a timeout.

## Design

- `ensure-server!` becomes non-blocking. It returns the tool names already
  registered for that server (or nil) immediately. If there is no live
  client, the server is not held, and no connect is in flight, it starts
  the connect on a background thread and returns nil for this turn. The
  tools land in the registry when the connect finishes and are part of the
  next turn's prompt (same path `recatalog!` already uses).
- One connect in flight per server (an atom slot); a second `ensure-server!`
  while it runs returns nil without spawning again.
- Failure hold with backoff instead of a flat 60 s: 60 s, 2 min, 4 min …
  capped at 15 min; reset on success. Log `:mcp/connect-failed` on each
  failure as today, plus `:mcp/connect-held` once per hold period (not per
  turn).
- `start!` (boot / reload) kicks off the same background connects and
  returns without waiting. Boot and config reload must not block on a
  dead server either.
- `call-server!` keeps its reconnect-on-demand for a registered tool whose
  process died (inside a tool call, bounded by the server's timeout) — out
  of scope here.
- No new config keys. `:timeout-ms` per server keeps its meaning for
  requests.

## Acceptance (isaac-mcp spec)

- [ ] A server whose command never answers `initialize`: `ensure-server!`
  returns within 100 ms on the first call and on every call while the
  connect is pending; exactly one process is spawned.
- [ ] When the background connect succeeds, the next `ensure-server!`
  returns the registered tool names and the tools resolve through the
  registry; no turn observed the connect latency.
- [ ] When it fails, the server is held; a call during the hold spawns
  nothing and returns nil; the hold doubles per consecutive failure up to
  the cap and resets after a success.
- [ ] `start!` with one dead server and one live server returns before the
  dead one's timeout; the live server's tools register.
- [ ] `stop!` cancels/abandons an in-flight connect without throwing and
  leaves no registered tools behind.
- [ ] Log events: `:mcp/connect-failed` per failure, `:mcp/connect-held`
  once per hold, `:mcp/connected` on success (existing).
- [ ] Version bump, manifest untouched, `bb spec` and `bb lint` green; the
  agent-side provider contract (`isaac.tool.registry/tool-providers`)
  needs no change — confirm by reading, not by editing.

Likely repo scope: isaac-mcp (runtime.clj, spec). Read-only in isaac-agent
(tool/registry.clj) for the provider contract.



## Exceptions

### features that require lens tools on the first turn (authorized, 2026-09-25, prowl@isaac-plan)

Micah's ruling stands: a turn never waits on an MCP server. Production never calls `start!`, so the first turn that allows `lens/*` sees no tools. The existing scenarios contradict that. Recut them; do **not** wire boot to `start!` (option b would re-block boot on a dead server).

Authorized feature edits on isaac-mcp (module main, then worker drops `@wip` only):

- Add one new step: **the MCP servers have connected** — calls `isaac.mcp.runtime/await-connects!` (bounded). Not a hand-`start!`.
- Insert that step **after** `When the Isaac system is started` and **before** the first assertion that the prompt offers or the turn invokes `lens__catalog`, in:
  - `features/turn.feature` — "crew glob lens/* offers prefixed MCP tools", "a turn invokes lens__catalog"
  - `features/lifecycle.feature` — "two servers with the same MCP tool name stay distinct", "a hung MCP call is a tool error"
  - `features/catalog.feature` — "a server that announces list_changed is re-catalogued for the next turn", "a server without listChanged keeps its catalog"
  - `features/hosts.feature` — not recut. Those scenarios run `isaac prompt` / `isaac acp` (a full process), not "the Isaac system is started". A background connect cannot finish inside that one process under "a turn never waits". Hosts scenarios stay red until a later bean (or a process-local warm-up). Do not block this bean on them.
- Do **not** add the await on scenarios that assert the tool is **absent** ("crew without lens…", "a dead lens server is not offered", "a dead command does not fail the turn"). Those must stay first-turn, no wait.
- Do **not** restore synchronous `ensure-server!` or a boot `start!` factory.

## Planner adjustment (2026-09-25, prowl@isaac-plan) — option (a): await-connects step, not boot start!

Conflict: runtime at `65cd3ce` is green (`bb spec` 40/0). `bb features` 13/8 because first-turn scenarios require `lens__catalog` before the background connect finishes. Ruling: a turn never waits.

**Decision: (a).** New step `the MCP servers have connected` backed by `await-connects!`. Not (b) — wiring `start!` into boot reintroduces the 30s tax on a dead server.

Planner already landed the step + inserts on isaac-mcp main `8ab0aa3`:
- `feature-steps/isaac/mcp_steps.clj` — `the MCP servers have connected` → `await-connects!`
- `features/turn.feature`, `lifecycle.feature`, `catalog.feature` — And-step after "the Isaac system is started" on the offer/invoke scenarios only

Worker now:
1. Rebase `bean/isaac-aswr` onto origin/main `8ab0aa3`. Keep runtime. Do not wire boot `start!`.
2. Confirm `bb spec` 40/0 and `bb features` on turn/lifecycle/catalog 0 failures. hosts.feature may still fail — not this bean.
3. Hand to verifier (no feature-baseline → exit 2). Do not land until those files are green.

This note resets the verify-fail counter.



## Planner note (2026-09-25, prowl@isaac-plan) — reverted 8ab0aa3; CI red was the planner commit

isaac-mcp main `8ab0aa3` (planner feature recut) failed CI Tests run [36084933542](https://github.com/slagyr/isaac-mcp/actions/runs/36084933542): `bb features` 13 examples, 6 failures. `the MCP servers have connected` NPEs — `requiring-resolve` of `isaac.mcp.runtime/await-connects!` is null. That var exists only on `bean/isaac-aswr` @ `65cd3ce`, not on main. Pinning features to unlanded runtime was the planner's error.

**Reverted** on isaac-mcp main: `3bdc096`. Main is back to `a28c098` tree for those files. Do not re-push the step until `await-connects!` is on main (this bean lands first, or the step lands in the same commit as the runtime).

Worker: rebase onto `3bdc096`. Keep runtime. Do not put `await-connects!` calls on main ahead of the function. Feature recut ships with the implementation commit, or after it — not before. Hand to verifier when `bb spec` and the named features are green **on the branch that contains both**.

## Worker note (2026-09-25, scrapper@isaac-work-2) — rebased on 3bdc096, recut ships with runtime

isaac-mcp `bean/isaac-aswr` @ `cac8478` (single commit on `3bdc096`): runtime from 65cd3ce unchanged + the planner's feature recut (8ab0aa3's step and And-inserts) in the same commit. Boot `start!` not wired.

Step fix: `await-connects!` alone was a no-op in features — nothing is pending after "the Isaac system is started" (no `start!`), so 8 still failed. The step now reaches each configured server through `ensure-server!` (the production tool-provider entry, same as a first turn) and awaits, one server at a time by sorted id (parallel connects made the ordered `:mcp/connected` log assertion in lifecycle "two servers…" racy).

Results on cac8478: `bb spec` 40/0, `bb lint` 0/0, `bb features` 13/2 ×3 runs — the only failures are hosts.feature (prompt, acp), deferred per planner. Note: hosts.feature is not @wip, so `bb ci` on main stays red on those two until the follow-up bean.

Race: a parallel session force-pushed `18a4a42` (based on the reverted 8ab0aa3; puts the warm-up inside `await-connects!`) to the branch at 02:10:45; my push replaced it. Preserved at `origin/bean/isaac-aswr-alt-18a4a42`.



## Verify fail (attempt 1, 2026-09-24): landing gate red — hosts.feature regresses 0→2 failures vs origin/main; planner ruling needed

HEAD: isaac-mcp origin/bean/isaac-aswr cac8478 (base 3bdc096 = origin/main)
Working tree: clean (fresh clone, perceptor@isaac-verify)

What passes (cac8478):
- bb spec: 40 examples, 0 failures, 117 assertions
- bb lint: 0 errors, 0 warnings
- Manifest: only :version 0.1.1→0.1.2. isaac-agent untouched (provider contract unchanged).
- Runtime read: ensure-server! is non-blocking (connect-async!, one :pending future per server), backoff hold 60s doubling to 15m and reset on success, :mcp/connect-held once per failure/hold, start! non-blocking, stop! bumps generation so an in-flight connect is abandoned. Matches the design.
- Feature edits: only the authorized "And the MCP servers have connected" inserts in turn/lifecycle/catalog (per ## Exceptions). No tampering.

What blocks landing:
- bb features on branch: 13 examples, 2 failures. Both in features/hosts.feature:
  1) "MCP tools reach every host — the prompt command offers and invokes an MCP tool"
  2) "MCP tools reach every host — an acp session invokes an MCP tool"
  got: "unknown tool: lens__catalog"
- bb features on origin/main 3bdc096 (same command, clean): 13 examples, 0 failures.
- So these are NOT pre-existing (verify.md §7). The bean introduces them, and landing makes main CI red. They are not @wip.
- This is a real user-visible regression: one-shot hosts (`isaac prompt`, `isaac acp`) lose all MCP tools, because their only turn happens before the background connect lands.

The planner said "do not block this bean on hosts". The landing gate (full suite green on main) still stands, and the worker cannot fix this without touching features. The worker's own note agrees main CI would stay red. I need a planner ruling on one of these:
  (a) authorize @wip on the two hosts scenarios, referencing a filed follow-up bean id, so main stays green; or
  (b) put a process-local warm-up for one-shot hosts (prompt/acp) in scope for this bean; or
  (c) explicitly authorize landing with main CI red (not recommended).

Hygiene: the beans repo has a stray file named `.beans/isaac-aswr--*.md` (a literal asterisk, 1.7KB older conflict note), committed alongside the real bean file. It should be deleted or merged in.



## Exceptions (addendum, 2026-09-25)

### hosts.feature (authorized @wip)

`features/hosts.feature` scenarios "the prompt command offers and invokes an MCP tool" and "an acp session invokes an MCP tool" may carry `@wip` until **isaac-x45o** lands. They are green on origin/main `3bdc096` and red under background connect (one-shot process, unknown tool: lens__catalog). `@wip` is the only authorized edit to that file. Do not rewrite the steps. Do not restore synchronous connect.

## Planner adjustment (2026-09-25, prowl@isaac-plan) — @wip the two hosts scenarios; do not land red

Verify fail: `bean/isaac-aswr` @ `cac8478` is `bb spec` 40/0, lint 0/0, authorized recuts on turn/lifecycle/catalog. Full `bb features` 13/2 vs 13/0 on origin/main `3bdc096`. Both failures are hosts.feature. Landing without `@wip` turns main CI red.

**Decision: (a).** Authorize `@wip` on those two scenarios. Follow-up owns the process-local warm-up. Not (b) in this bean. Not (c) — do not land red.

Also: delete stray `.beans/isaac-aswr--*.md` (literal asterisk).

### Worker now

1. Add `@wip` on the two hosts scenarios only (authorized above). Reference the follow-up bean id in a one-line comment if the file already has a bean comment style; otherwise the Exceptions entry is enough.
2. Confirm `bb features` 0 failures (the two `@wip` excluded) and `bb spec` 40/0.
3. Hand to verifier. Do not wire boot `start!`. Do not rewrite hosts steps.

This note resets the verify-fail counter.

## Worker note (2026-09-25, scrapper@isaac-work-1) — @wip hosts scenarios; to verify

isaac-mcp `bean/isaac-aswr` @ `b479d0c` (on `cac8478`, base `3bdc096` = origin/main). The only change is `@wip` on the two authorized hosts.feature scenarios ("the prompt command offers and invokes an MCP tool", "an acp session invokes an MCP tool"). Steps are unchanged. No synchronous connect and no boot `start!`. isaac-x45o owns the warm-up.

Results: `bb spec` 40/0/117, `bb features` 11/0 (13 minus the 2 @wip), `bb jvm-features` 11/0, `bb lint` 0/0. `bb bean-gate verify` exit 2 (no feature-baseline), so this goes to verify.
