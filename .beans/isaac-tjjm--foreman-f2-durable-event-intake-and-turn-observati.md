---
# isaac-tjjm
title: 'Foreman F2: durable event intake and turn observations'
status: todo
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T23:35:30Z
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
