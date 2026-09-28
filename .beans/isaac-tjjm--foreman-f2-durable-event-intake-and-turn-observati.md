---
# isaac-tjjm
title: 'Foreman F2: durable event intake and turn observations'
status: completed
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-28T00:12:32Z
parent: isaac-q3u3
---

Likely repo: **isaac-foreman** (with its HTTP route contribution). Design: Micah + planner, 2026-09-27. Builds on completed F1 (isaac-mjr4) and the existing turn-observer seam (isaac-bbov).

## Contract to plan

- The crew `signal` tool, `POST /foreman/events`, CLI signal, and turn observers write a common event envelope to one durable intake before acknowledging it.
- Event IDs deduplicate retries; consuming an event transitions one machine instance at most once. Startup resumes unconsumed events. State/history remain Foreman's authority; beans may be a later view, not the store.
- Observe turn start/end/death and carry source request ID plus machine/instance identity. A turn that ends without an expected semantic signal yields a backstop event the machine can handle.
- Keep events independent of Hail delivery and session selection. Preserve F1's unhandled-event history behavior.

## Scenario plan to review

1. Tool and HTTP send the same event shape; acknowledged events survive restart.
2. Repeated event ID changes state once and is visible in history.
3. A turn ending without its signal produces a handled backstop transition.
4. An unhandled observation is recorded without corrupting instance state.

Draft until scenarios are committed and baselined. Beans mirroring remains deferred as recorded in isaac-tdgt.


## Decisions (2026-09-27, Micah + planner)

1. **Intake = the instance's existing `events.ednl`** (F1 already designed history and intake as one file). An arriving event is appended as `received` before it is acknowledged; consuming it appends the transition or unhandled record carrying the event id. A received event with no result is unconsumed. No second store.
2. **Consumption:** immediately after the ack, in the same process. Before any new event for an instance is handled, its older unconsumed events are applied first, in order. Server start sweeps every instance (unit spec). No background worker.
3. **Event ids:** caller-supplied or generated. A repeated id is acknowledged as a duplicate and changes nothing.
4. **Unknown machine or instance:** refused, never acknowledged (F1 behavior kept).
5. **Turn observations:** Foreman registers a `foreman` turn observer; a turn joins an instance via the ref `foreman:<machine>/<instance>` (existing Agent seam). Events: `:turn-started`, `:turn-ended`, `:turn-failed`, `:turn-died`. **No "expected signal" concept:** if the crew signaled, the instance already moved, so a `:turn-ended` row keyed on the old state *is* the backstop. Supersedes the "backstop event" wording above.
6. **Crew tool `foreman-signal`** (matches `hail-send`): args `machine`, `instance`, `event`, optional `data`, `id`. All explicit; defaulting from the turn's observer ref is a later nicety.
7. **HTTP `POST /foreman/events`:** JSON or EDN, server token auth. 202 with the event id; duplicate → 202 with `"duplicate": true`; unknown instance → 404.
8. **Envelope:** `{:id :machine :instance :event :data :source :at}`; `:source` ∈ `:tool :http :cli :observer`; tool events also record crew and session. `:data` is stored in history, not interpreted (actions using it are isaac-lr8h).

## Scenario plan (approved 2026-09-27) — `isaac-foreman/features/foreman/events.feature`

1. The signal tool moves an instance; history shows source tool + crew.
2. HTTP sends the same shape: 202 + event id; unknown instance refused, nothing acknowledged.
3. A repeated event id (CLI then HTTP) changes state once; history shows the duplicate.
4. An acknowledged-but-unconsumed event is applied before the next one, in order.
5. A turn that signals: instance moves on the signal; the later `:turn-ended` is recorded unhandled.
6. A turn that ends without signaling takes the `:turn-ended` backstop row; a failed turn takes `:turn-failed`.


## Acceptance

Feature: `isaac-foreman/features/foreman/events.feature` (whole file, 6 scenarios, committed `@wip` on main at 1499e3f). Remove `@wip`; all pass:

- [ ] `bb features features/foreman/events.feature` (from isaac-foreman)
- [ ] Individually: `:23` signal tool, `:47` HTTP, `:72` duplicate id, `:91` resume unconsumed, `:109` signaling turn, `:131` backstop + failed turn
- [ ] Unit spec: server start sweeps every instance's unconsumed events (not a scenario).
- [ ] Unit spec: `:turn-died` is emitted from the observer's `on-turn-died` (no scenario can trigger a death).
- [ ] Existing `cli.feature` and `machine.feature` stay green (status history lines gain `[<id>] via <source>` after the transition text).
- [ ] `bb verify` green; version bump in `resources/isaac-manifest.edn`.

Likely scope: isaac-foreman — `store.clj` (received/consumed records, dedupe, unconsumed scan), `core.clj` (intake + drain before handling), `cli.clj` (`--id`), new `foreman-signal` tool (`:isaac.agent/tools`), `POST /foreman/events` (`:isaac.http/route`), `foreman` turn observer registered with Agent's `isaac.drive.observer`.

feature-baseline: isaac-foreman 1499e3f9f14ce16c593a3952ca070019e378ff4b
feature-blob: isaac-foreman features/foreman/events.feature f1d57799352c714fc891b69de3386c63ba919f9b

## Work checkpoint (2026-09-27)

Done: rebased bean branch onto 04935d8; registered `:foreman/signal`, preserved the corrected feature with only file-level @wip removed; fixed HTTP/observer root resolution and tool caller metadata; separated newline-less injected received rows on append. `ISAAC_TEST_TIMEOUT_MS=120000 bb features features/foreman/events.feature` green (6 examples, 29 assertions); focused store spec green (7 examples). Committed and pushed d599c83 on `bean/isaac-tjjm`. Next: run `bb spec`, `ISAAC_TEST_TIMEOUT_MS=120000 bb ci`, individual acceptance selectors, then `bb bean-gate verify isaac-tjjm` from isaac and land if exit 0. Resume at `src/isaac/foreman/store.clj:27` (append boundary) and `src/isaac/foreman/tool.clj:9` (crew/session metadata). Default 60s JVM feature wrapper timed out *after* 6/0 reporting; 120s override exited 0.

## Acceptance conflict (2026-09-27)

Focused CLI preflight diagnosis in scenario :23: the unchanged step `the crew "bartholomew" allows tools: "foreman-signal"` writes `:tools {:allow [:foreman-signal]}`; production config validation refuses `crew.bartholomew.tools.allow[0]: must be a namespaced keyword (ns/name or ns/*); got :foreman-signal`. The tool contract is a bare name, while the current Agent policy only permits namespaced tool IDs. This cannot be fixed by changing the baselined .feature (worker is limited to @wip removal); planner needs to decide whether to change the tool identity/feature or authorize a namespace-compatible mapping. Separately scenarios :47/:72 send `Authorization: Bearer secret123` without configuring the server token: current HTTP middleware refuses unknown credentials with 401 before the route; expected 202 is unreachable without a configured token. Current `bb features features/foreman/events.feature`: 6 examples, 5 failures; core + CLI JVM specs: `bb spec` 32 examples, 0 failures. `bb bean-gate verify isaac-tjjm` passes contract-only, not acceptance. Work remains on bean branch 0aa8957; no landing attempted.


## Planner adjustment (2026-09-27, prowl@isaac-plan) — namespaced allow, admin principal

Both conflicts are the feature, not the product. Do not change the tool's wire name. Do not add a scope to the route under this bean.

### Tool allow

`the crew … allows tools:` writes keywords into `:tools :allow`. Agent rejects an unqualified keyword (`must be a namespaced keyword`). The model still calls `foreman-signal`. The allow token is `foreman/signal`, which is `:foreman/signal`.

Both allow steps now say `foreman/signal`. The `tool_call` column stays `foreman-signal`.

### HTTP auth

`POST /foreman/events` declares no `:scope`. A route with no scope requires admin (`:*`). `Bearer secret123` with no principal is an unknown bearer, so the middleware returns 401 before the route. That is current HTTP, not a Foreman bug.

Background now configures principal `keeper` with secret `secret123` and scopes `*`. The Bearer rows stay. 202 and 404 are reachable. Do not declare a route scope here. A later bean can if a non-admin caller should post events.

### Re-baselined

isaac-foreman main `1443cd8`. The feature file is not `@wip`; the six scenarios are this bean's because the blob names no lines. Newest lines in force:

    feature-baseline: isaac-foreman 1443cd8c364bea435c660c042e3d0f6b6bff8c5c
    feature-blob: isaac-foreman features/foreman/events.feature ca61b8fa436b02204adea79dae31ded1b8f43bde

### Worker now

1. Rebase `bean/isaac-tjjm` onto `1443cd8`. Keep the implementation (`0aa8957`). Feature diff may only drop `@wip`, and there is none left to drop.
2. Do not rename the tool. Do not add `:scope` to the route. Do not put the allow token back to `foreman-signal`.
3. `bb features features/foreman/events.feature` green, then `bb bean-gate verify isaac-tjjm` exit 0, then land.

This note resets the verify-fail counter.

feature-baseline: isaac-foreman 1443cd8c364bea435c660c042e3d0f6b6bff8c5c
feature-blob: isaac-foreman features/foreman/events.feature ca61b8fa436b02204adea79dae31ded1b8f43bde


## Planner correction (2026-09-27, prowl@isaac-plan) — 1443cd8 broke foreman main CI

CI run 36359997829 failed `bb ci` on that commit. I had dropped the file-level `@wip` and changed the allow token and auth in a feature whose steps are not implemented yet. `bb ci` runs `@wip` scenarios. `foreman/signal` does not cover the wire name `foreman-signal` (`matches?` wants `foreman__signal`). The model reported `unknown tool: foreman-signal`. The HTTP scenarios still did not reach 202.

Reverted. isaac-foreman main `df12bd2` restores the `@wip` feature text from `1499e3f`. The in-force baseline is that text again. Do not treat `1443cd8` as the contract.

    feature-baseline: isaac-foreman df12bd21649c98415ea72f589e57484fb4ff6e19
    feature-blob: isaac-foreman features/foreman/events.feature f1d57799352c714fc891b69de3386c63ba919f9b

### What the worker still has to satisfy

The two conflicts stand. They are not fixed by editing the feature to a token the allow step cannot express.

1. **Allow.** `the crew … allows tools: "foreman-signal"` writes `:foreman-signal`, and Agent rejects an unqualified keyword. `foreman/signal` is a legal allow token but does not match the wire name `foreman-signal`. The tool's registered wire name has to be what a namespaced allow covers (`foreman__signal` if the allow token is `foreman/signal`), and the scenario's `tool_call` column has to use that wire name. That is a feature edit. The planner will make it once the worker confirms the registered name. Do not land a feature edit that drops `@wip` before the scenarios pass — `bb ci` on main runs them.

2. **HTTP.** `POST /foreman/events` declares no scope, so it requires admin. `Bearer secret123` with no principal is 401. Configure principal `keeper` with secret `secret123` and scopes `*` in the scenario (or declare a route scope and a principal that holds it). Also a feature edit. Same rule: not on main until the scenario is green.

### Worker now

Keep the implementation on `bean/isaac-tjjm`. Do not rebase onto `1443cd8`. Report the wire name the tool is registered under, and whether a principal step in Background is enough for the POST scenarios. The planner will rewrite the feature, commit it `@wip` on main, and re-baseline. Then you drop `@wip` only after `bb features features/foreman/events.feature` is green.

This note resets the verify-fail counter.

feature-baseline: isaac-foreman df12bd21649c98415ea72f589e57484fb4ff6e19
feature-blob: isaac-foreman features/foreman/events.feature f1d57799352c714fc891b69de3386c63ba919f9b

## Worker diagnosis for planner correction (2026-09-27, scrapper@isaac-work-3)

The current `resources/isaac-manifest.edn` berth key is `:foreman-signal`. Agent's `register-tool-entry!` applies `isaac.tool.names/wire-name` to that unqualified keyword, so the registered/model wire name is **`foreman-signal`** (not `foreman__signal`). `:foreman/signal` is a valid allow token, but its wire name is `foreman__signal` and it does **not** cover the current registration. For a namespaced allow the tool berth itself must change to `:foreman/signal`, which registers as `foreman__signal`; then both the feature allow token `foreman/signal` and Grover `tool_call` `foreman__signal` can match. Please confirm the intended registration/name contract in the new baseline. The worker has not renamed the tool or edited the baselined feature yet.

`POST /foreman/events` declares no scope. HTTP's existing principal step in Background — `And principal "keeper" is configured with secret "secret123" and scopes "*"` — writes a SHA-256 bearer hash and `:*` scope. This is sufficient for the Bearer `secret123` POST rows to pass admin authorization, assuming the shared HTTP server step is loaded as it is for this feature; there is no need for a route scope or product auth workaround. The HTTP server's own `features/server/principals.feature` demonstrates `scopes "*"` reaching an unscoped route.

Implementation remains on `bean/isaac-tjjm` at `0aa8957`, ahead of foreman main `df12bd2`; only its removal of `@wip` differs from the in-force feature baseline. Do not land until the planner commits a corrected `@wip` feature on main and re-baselines.


## Planner adjustment (2026-09-27, prowl@isaac-plan) — wire name is foreman__signal

Worker confirmed: berth key `:foreman/signal` registers wire `foreman__signal`. Unqualified `:foreman-signal` registers `foreman-signal`, which Agent will not allow. A Background principal with secret `secret123` and scopes `*` is enough for the unscoped route. No route scope.

Rewritten on isaac-foreman main `04935d8`. File stays `@wip`, so `bb ci` on main skips these scenarios (verified: `bb jvm-features -t '~@wip'` does not run this file; the other foreman features are 7/0).

- Allow token: `foreman/signal` (both steps).
- `tool_call` column: `foreman__signal` (both tables).
- Background: principal `keeper`, secret `secret123`, scopes `*`.
- Bearer rows stay.

### Re-baselined

    feature-baseline: isaac-foreman 04935d837509cd0bfaf8955faabb7894594ba30d
    feature-blob: isaac-foreman features/foreman/events.feature f04030887455f807a29d6be80dcdbc450c767779

The file is `@wip`, so the blob names no lines. All six scenarios are this bean's. Dropping the file `@wip` is the worker's feature diff, and only after they pass.

### Worker now

1. Change the berth key from `:foreman-signal` to `:foreman/signal` so the registered wire name is `foreman__signal`. Rebase `bean/isaac-tjjm` onto `04935d8`. Keep the rest of the implementation.
2. Feature diff may only drop the file-level `@wip`. Do not put the tool_call column back to `foreman-signal`. Do not add a route scope.
3. `bb features features/foreman/events.feature` green, then `bb bean-gate verify isaac-tjjm` exit 0, then land. Do not land while the file is still `@wip` on main — that is what broke CI last time.

This note resets the verify-fail counter.

feature-baseline: isaac-foreman 04935d837509cd0bfaf8955faabb7894594ba30d
feature-blob: isaac-foreman features/foreman/events.feature f04030887455f807a29d6be80dcdbc450c767779

## Work checkpoint (2026-09-27, scrapper@isaac-work-1)

Done: rebased implementation on corrected 04935d8 planner baseline, switched berth to :foreman/signal (wire foreman__signal), removed only @wip. Red: `bb features features/foreman/events.feature` runs 6 examples, 4 failures, 15 assertions. Tool/turn observation and backstop scenarios now advance, but tool history assertion, HTTP (404 instead of 202), and unconsumed CLI output remain red. No green full run; do not land. Next: inspect direct-response registration in `../isaac-http/spec/isaac/http/server_steps.clj:545` and `src/isaac/foreman/core.clj:136` (history-line) to identify why route not registered and CLI status history misses expected pattern; run focused `bb features features/foreman/events.feature:47`, then full feature and gate.

## Landed on main (2026-09-27)

main-sha: isaac-foreman c4a61d4c24c1a74d2d5c8bb8537471028ac9ea8d

The corrected implementation passes `bb features features/foreman/events.feature` (6/0), `bb ci` (33 specs and 13 features, 0 failures), and `bb bean-gate verify isaac-tjjm` (exit 0) on the squash commit. The earlier checkpoint's red result predates the fix.
