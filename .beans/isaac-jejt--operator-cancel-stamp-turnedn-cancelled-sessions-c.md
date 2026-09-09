---
# isaac-jejt
title: 'Operator cancel: stamp turn.edn :cancelled; sessions cancel + hail/resume honor it'
status: draft
type: feature
priority: normal
created_at: 2026-09-09T20:46:22Z
updated_at: 2026-09-09T20:46:22Z
---

## Problem

ACP Escape already cancels in-flight turns (`session/cancel` → `bridge/cancel!`). Hail turns and operator CLI have no equivalent. `isaac sessions list --in-flight` can *see* busy sessions in the same JVM; it cannot stop them. A second-terminal `isaac sessions …` is a cold process and cannot see `in-flight*` or `turns*` at all.

Worse: hail's delivery `cond` has no cancelled branch. `{:stopReason "cancelled"}` falls through to **delivered**.

## Why not HTTP / in-process /cli / eqkb

`bridge/cancel!` and `in-flight*` live in the server JVM. Agent does not know about the server; the server does not know about sessions. ACP works because it is a comm loaded *into* that JVM.

In-process `/cli` (and parked isaac-eqkb warm CLI) would help **remote** only. Local `isaac sessions cancel` is still a cold process. Do not smuggle a POST-to-server into this bean. Do not teach agent about HTTP.

## Design (approved 2026-09-09)

Cancel is a **disk signal on the turn marker** the SessionStore already owns (`sessions/<id>/turn.edn`, isaac-7li9). Marker exists ⇒ something to cancel (live, orphan, or suspended). Missing ⇒ refuse.

### Store (only writer of the file)

Add `request-cancel!` (name flexible) on SessionStore:

- no marker → return false; create nothing
- marker present → stamp `:cancelled true` on the existing map, return true

Nobody opens `turn.edn` except the store. CLI / `cancelled?` / resume go through the protocol. Do not add a sibling cancel file.

One cheat already exists: `bridge.resume` also calls `store-common/clear-turn-marker!*` after the protocol clear. Do not add another.

### CLI — `isaac sessions cancel <id>` (isaac-agent)

- Missing id / unknown session / no marker → refuse, exit 1. **Do not** call `cancel!` on idle — that plants an atom flag that poisons the next prompt (ACP relies on that race; CLI must not).
- Marker present → `request-cancel!`, exit 0. Fire-and-forget: do not wait for the tool/SSE to unwind.
- Do **not** call `bridge/cancel!`. That atom is the other JVM's. Same-JVM tests still work because `cancelled?` reads the marker.

Surface is session-only. No `isaac hail cancel`. Find the bound session with `sessions list` / the marker.

### `cancelled?` (isaac-agent)

Atom **or** `(true? (:cancelled (get-turn-marker …)))`. Already polled at every tool-loop iteration and every ~50ms of LLM SSE. Do not add a second "drive loads the file" path.

ACP Escape unchanged (still the atom).

### Live hail (isaac-hail)

`run-turn!` returning cancelled must **not** fall through to delivered. Archive to `hail/cancelled/` (sibling of `delivered/` and `failed/`), log `:hail/turn-ended :outcome :cancelled`, not requeueable. Delivery file is already gone at claim; the worker still has the in-memory delivery.

### Resume (isaac-agent + isaac-hail)

A leftover `:cancelled` marker is "don't resume," not "a live turn in this JVM."

- `:hail` + `:cancelled` → write `hail/cancelled/`, delete marker, do **not** re-queue to `deliveries/`.
- `:comm` / `:cron` / `:cli` + `:cancelled` → drop marker, do **not** dispatch the interruption note.
- Then delete the marker (re-queue/archive first, delete second — same F4 ordering as vdfc).

Idle poison: next dispatch rewrites `turn.edn` without `:cancelled`. A leftover stamp only matters if resume runs first — and resume drops it.

User cancel still **deletes** the marker when the live turn finishes (isaac-2xj5: cancel means stop, don't come back). The stamp is the signal; deletion is still the completion.

## Out of scope

- Discord / iMessage stop
- `isaac hail cancel`
- Force-kill of a running exec (cooperative cancel stands)
- Parked-queue drop
- Making `sessions list --in-flight` see another process's marker (same hole as ✈️; later bean)
- isaac-eqkb (parked)
- Server POST / `:isaac.server/route`

## Homes

| Piece | Module |
|---|---|
| `request-cancel!`, `cancelled?` reads marker, `sessions cancel` | isaac-agent |
| hail archive + log; resume hail branch | isaac-hail |
| resume comm drop | isaac-agent (`bridge.resume`) |

## Decisions (2026-09-09, Micah)

- Surface: `isaac sessions cancel <id>` only. Not `hail cancel`.
- Idle / unknown / missing id: refuse, exit 1. Do not call `cancel!`.
- Hail outcome: `hail/cancelled/`, `:outcome :cancelled`, not delivered, not failed, not requeueable.
- Fire-and-forget.
- Cross-process: stamp `:cancelled` on the existing turn marker. No HTTP. No in-process-/cli requirement.
- Store owns the file; add an update (`request-cancel!`), not a get-and-spit from CLI.
- ACP unchanged.

## Scenario plan (10) — pending review

`features/session/cli.feature` (isaac-agent)
1. help lists cancel
2. missing id refuses
3. unknown session refuses (no marker created)
4. idle session refuses (no marker created)
5. live turn: stamp `:cancelled` and return; still in-flight (did not wait)
6. orphan marker: stamp and return

`features/bridge/cancel.feature` (isaac-agent)
7. drive honors the stamp → turn result cancelled; marker gone

`features/delivery.feature` (isaac-hail)
8. live hail archives to `hail/cancelled/`; not delivered/failed/deliveries; log `:outcome :cancelled`

`features/turn-resume.feature` (isaac-hail)
9. cancelled hail marker is not re-queued; lands in `hail/cancelled/`; marker gone

`features/session/resume_repair.feature` (isaac-agent)
10. cancelled comm marker is dropped; no interruption note; marker gone
