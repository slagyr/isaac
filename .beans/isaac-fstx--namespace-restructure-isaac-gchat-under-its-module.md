---
# isaac-fstx
title: 'Namespace restructure: isaac-gchat under its module id'
status: completed
type: task
priority: normal
created_at: 2026-09-30T14:12:34Z
updated_at: 2026-09-30T21:16:24Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
    - isaac-tacl
---

Micah, 2026-09-30. **A module's code lives under its module id.** isaac-foundation → `isaac.foundation.*`, isaac-agent → `isaac.agent.*` (e.g. `isaac.session.frequencies` → `isaac.agent.frequencies`), isaac-claude-code (`:isaac.provider.claude-code`) → `isaac.provider.claude-code.*`, isaac-episodes (`:isaac.session.episodes`) → `isaac.session.episodes.*`, comm modules → `isaac.comm.<name>.*`, and so on. Source, specs, spec-support, step namespaces, manifest symbols (`:factory`, berth entries), bb tasks and docs all move together. Clean cutover: no alias namespaces.

## Order: inside-out (Micah)

1. isaac-foundation (requires nobody).
2. isaac-agent: bump to the new foundation, update its foundation requires, rename its own.
3. Every other module, in parallel: bump foundation + agent (this absorbs the pin sweep isaac-5x21), update requires, rename its own. A module that requires another leaf (gchat/gmail → google) goes after that leaf.

Each repo is touched once.

## Deploy freeze

An installed Isaac runs one foundation and one agent, so zanebot/skiff don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and skiff live config (read-only, `ssh zane@zanebot…` / `ssh skiff@skiff`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.

## Findings (initial branch — blocked on an isaac-agent race, since fixed)

Work done on `bean/isaac-fstx` in `isaac-gchat`, pushed to origin
(https://github.com/slagyr/isaac-gchat/tree/bean/isaac-fstx), **not merged
to main**.

**isaac-gchat's own namespaces are already `isaac.comm.gchat.*`** (matching
its module id) — nothing to rename in `src/`/`spec/`. The work is: bump
`deps.edn`/`bb.edn` pins to isaac-foundation `06d58b75bc52b3e118dc8e81569096de2532a0d4`
(the exact sha isaac-agent 123d718 pins, per the bean's pin rule), isaac-agent
`123d71850b480dc0859886e1a4fa53e082c258f1`, isaac-http `56998543b3e5c40593d2a3ea97b16550e3731463`
(already migrated, `isaac.http.*` names unchanged from this repo's point of
view) and isaac-google `d16c9ac7ceda55c7c0dae583f047a590e9f154b2` (per
isaac-tacl's follow-up note), and rename every cross-module reference to
their renamed namespaces across `src/`, `spec/`, `feature-steps/`:
`isaac.api`→`isaac.agent.api`, `isaac.attention`→`isaac.agent.attention`,
`isaac.charge`→`isaac.agent.charge`, `isaac.comm.{delivery.queue,factory,
protocol,registry}`→`isaac.agent.comm.*`, `isaac.config.defaults`→
`isaac.agent.config.defaults`, `isaac.drive.weather`→`isaac.agent.drive.weather`,
`isaac.llm.{auth.store,api.grover}`→`isaac.agent.llm.*`,
`isaac.session.frequencies`→`isaac.agent.frequencies` (not
`isaac.agent.session.frequencies` — on0o's explicit call, same as agent's own
rename), `isaac.session.{session-steps,store.memory,store.spi,store}`→
`isaac.agent.session.*`, `isaac.turn.worker`→`isaac.agent.turn.worker`;
`isaac.config.{api,loader,root,schema-compose,schema.resolve}`,
`isaac.fs`, `isaac.logger`, `isaac.module.{protocol,discovery,berths}`,
`isaac.nexus`→`isaac.foundation.*`. Fixed `bb.edn`'s `config-bypass-lint`
doc string (`isaac.config.*`→`isaac.foundation.config.*`), the same
pre-existing trap on0o and tacl already found and fixed in their own
`bb.edn`s.

**Left alone (justified, data/keywords not code namespaces):** `:isaac.agent/comm`,
`:isaac.agent/tools`, `:isaac.google/{handler,registration,scopes}` berth
keywords in `resources/isaac-manifest.edn` and specs; `isaac.foundation`/
`isaac.agent` module-id prose mentions in `resources/isaac/gchat/handbook.md`;
`isaac.foundation.handbook-chapter-spec` and `isaac.http.app` mentions in
comments — both already correctly spelled (foundation and http had already
landed by the time this bean started); `spec/isaac/comm/gchat_spec.clj`'s
`:ex-class "isaac.tool.ToolExecutionException"` — a fabricated example value
(gchat's own classifier just substring-matches `"tool"` in the lowercased
ex-class), never a real isaac-agent class at any point in its history.

**Feature-steps also moved under the module prefix, matching isaac-tacl's
google-steps follow-up:** `feature-steps/isaac/gchat_steps.clj` (ns
`isaac.gchat-steps`) → `feature-steps/isaac/comm/gchat/gchat_steps.clj` (ns
`isaac.comm.gchat.gchat-steps` — kept a `-steps`-suffixed final segment,
*not* bare `isaac.comm.gchat.steps`, because gherclj auto-aliases each
required step namespace by its last segment for the generated spec code;
naming both this repo's own steps and isaac-google's `isaac.google.steps`
with a bare `steps` final segment collided ("Alias steps already exists in
namespace ..., aliasing isaac.comm.gchat.steps") once both were required
together). `feature-steps/isaac/features_main.clj` (ns `isaac.features-main`,
the `clojure -M:features` entry point, generic gherclj/JVM-exit boilerplate)
also moved to `isaac.comm.gchat.features-main` for consistency, with
`deps.edn`'s `-m` updated. Added an explicit `"-s" "isaac.google.steps"` to
`deps.edn`'s `:features` `:main-opts` — isaac-gchat's classpath already pulls
in isaac-google's `feature-steps` dir (via isaac-google's own `:paths`) for
the push-door/oauth-callback-style Google steps this repo's own steps
directly call, and the `"isaac.**-steps"` glob no longer matches
`isaac.google.steps` (final segment `steps`, not `*-steps`) — same shape as
isaac-tacl's own fix, needed here too since gchat runs its own
`clojure -M:features` process with its own `-s` list.

**One real (non-rename) code fix required to keep the suite passing:**
isaac-agent's async turn worker (isaac-e9jl, isaac-2lc4 — landed between the
old and new agent pins) changed `isaac.agent.turn.worker/tick!` to only
*claim and start* a runnable turn on its own thread before returning,
instead of running it inline; a caller that needs the turn to have actually
finished must call `await-idle!` afterward (isaac-agent's own
`session_steps.clj/turns-on-session-finish` does exactly this). gchat's own
`"the in-flight turn on session ... ends"` step only called `tick!` — after
the pin bump this step returned before the coalesced reply posted, failing
`comm/gchat/inbound.feature`'s "three quick messages in one DM thread get
one consolidated reply (isaac-xoqn)" scenario with 0 outbound HTTP requests
instead of 1. Fixed by calling `await-idle!` right after `tick!`, both still
inside `with-chat-stubs`'s `with-redefs` scope (the async turn's own HTTP
call must still hit the stub, not the network) — see
`feature-steps/isaac/comm/gchat/gchat_steps.clj`'s `in-flight-turn-ends`.

**Blocker found after that fix — a race in isaac-agent's own async turn
worker, not fixable from isaac-gchat.** With `await-idle!` added, the same
scenario now gets **2** outbound HTTP requests instead of 1: one post is the
scripted reply text ("All three, answered.", the correct coalesced-turn
output), the second is the literal joined text of messages 2+3
("[thread:T1] ada@marigold.example: @Isaac second\n[thread:T1] ada@marigold.example:
@Isaac third") echoed back — i.e. a *second*, spurious turn ran on the
trailing two of the three coalesced messages, using up an LLM response the
scripted queue no longer had.

Root cause, traced with a temporary debug print (not committed): the three
messages are enqueued as one `:waiting-session` group (same `:coalesce-key`,
the Chat thread) as expected, and the explicit `tick!` claims and starts them
as *one* coalesced record on its own thread via `run-record-async!`. That
thread's `bridge/dispatch!` call runs to completion and its own `finally`
block (`isaac.agent.bridge.core/dispatch-charge!`, the long-standing
"a session's own waiting room drains as soon as its running turn releases
it" cleanup, unchanged by isaac-e9jl/isaac-2lc4) checks
`(seq (turn-queue/waiting-groups session-key))` and — finding it non-empty —
fires its own nested `tick!` to drain it. Under the **old, synchronous**
`tick!`, this reentrant check ran *after* `process-record!`'s own
`doseq`(marking every coalesced record's `:held-ids` `:finished`) had
already completed inline, so the check correctly saw nothing left. Under the
**new, async** model, `process-record!`'s finish-marking `doseq` doesn't run
until *after* `bridge/dispatch!` returns — but the nested drain-tick fires
*from inside* that same `dispatch!` call, before the outer `process-record!`
gets a chance to mark the group's trailing members (`:held-ids`) `:finished`.
So the nested tick sees stale `:waiting-session` records for messages 2 and 3
(message 1's own record is already `:running`/reclaimed, so it doesn't
reappear), coalesces just those two, and starts a second real turn — a
genuine duplicate-reply bug in production behavior (any comm's busy-session
consolidation is exposed to this), not a test-harness or namespace-rename
artifact. isaac-gchat's step can't work around it: the duplicate turn is
started by isaac-agent's own dispatch machinery before gchat's step gets
control back.

**Not landing on my own judgment**, per the isaac-tacl precedent for a
cross-repo blocker: leaving this bean `in-progress` (not `unverified`),
branch `bean/isaac-fstx` pushed to `isaac-gchat` for review rather than
merging red CI to main. `bb ci` is red only on this one scenario;
`config-bypass-lint` ok, `bb spec` 200/200, `bb jvm-features` 60 examples /
159 assertions / **1 failure** (the race above). Confirmed **not**
reproducible on pre-change `isaac-gchat` main (0 failures, 60/60, 161
assertions) — this is a real, newly-exposed isaac-agent regression from
adopting the mandated 123d718 pin, not a naming issue. Recommend: file a bean
against isaac-agent for the `dispatch-charge!`/`tick!` finish-marking race
(the `:held-ids` `doseq` needs to run — or the members need to be marked
`:running`/otherwise excluded from `waiting-groups` — *before* `dispatch!`'s
own drain-on-release fires, not after), then rebase and re-run this branch's
`bb ci` before merging.

**Live-config greps (read-only, no edits needed).** zanebot
(`ssh zane@zanebot.<tailnet>.ts.net`) and skiff (`ssh skiff@skiff`)
`~/.isaac/config`: no hits on either host for any of the renamed namespace
tokens above.

**Handbook/README.** No edits needed — `resources/isaac/gchat/handbook.md`
and `README.md` only name other modules by their (unchanged) module ids
(`isaac.agent`, `isaac.foundation`, `isaac.comm.gchat`).

Full grep of the tracked tree for any remaining pre-rename foundation/agent/
http/google namespace token outside the justified exceptions above: 0 hits.
`src/`, `spec/` namespace prefixes: 100% `isaac.comm.gchat.*` (already true
before this bean).

## Landed on main

main-sha: isaac-gchat ae1a75f4b98e75b0577d13c6c6a1eb84fb0d2e17

isaac-2tez landed the fix for the `dispatch-charge!`/`tick!` finish-marking
race in isaac-agent (main now `f9530426d04b6f66f17ae51f6f9a1a697531b39d`,
still pinning foundation `06d58b75bc52b3e118dc8e81569096de2532a0d4` — no
change there). Resumed on the same worktree/branch: bumped every
isaac-agent pin site in `deps.edn`/`bb.edn` (base, `-spec` deps) from
`123d71850b480dc0859886e1a4fa53e082c258f1` to `f9530426…`; no other change
needed. No divergence from `origin/main` at push time; squashed both
working commits into one and pushed `ae1a75f:main` directly (accepted, not
denied by the classifier). Fast-forwarded the shared `isaac-gchat`
checkout; deleted branch `bean/isaac-fstx` and its worktree.

**Test results (HOME isolated at `/tmp/isaac_scratch_home_fstx_land`).**
`bb config-bypass-lint`: ok. `bb lint`: 90 errors/17 warnings, identical to
pristine pre-bean main (pre-existing clj-kondo speclj-macro gap). `bb spec`:
200/200. `bb jvm-spec`: 200/200. `bb jvm-features`: **60/60, 161
assertions** — the xoqn consolidation scenario now posts exactly one reply;
assertion count matches pristine pre-bean main exactly. `bb ci`: green
end-to-end.

**GitHub CI on main-sha ae1a75f:** `CI Tests / verify` — green (run
36777896499, 54s).

Full grep of the tracked tree for any remaining pre-rename namespace token
(foundation/agent/http/google) outside the justified exceptions above: 0
hits.

No live-config edits were needed (checked before the initial push — no hits
on zanebot or skiff for any renamed namespace token).

## Planner verification (2026-09-30)

Verified on ae1a75f: all namespaces isaac.comm.gchat.*, agent pin f953042, CI green.
