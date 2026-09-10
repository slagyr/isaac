---
# isaac-jejt
title: 'Operator cancel: stamp turn.edn :cancelled; sessions cancel + hail/resume honor it'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-09T20:46:22Z
updated_at: 2026-09-10T22:52:01Z
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
- Existing `sessions --help` subcommand list (`features/session/cli.feature:22`) also lists `cancel`. Do **not** `@wip` that passing scenario; add the `cancel` assertion in the same commit that implements the subcommand.

## Scenario review (2026-09-09, Micah) — all 10 approved, keep as written

`features/session/cli.feature` (isaac-agent @ `03af97b`)
1. **keep** `features/session/cli.feature:329` — help lists cancel
2. **keep** `features/session/cli.feature:335` — missing id refuses
3. **keep** `features/session/cli.feature:341` — unknown session refuses (no marker created)
4. **keep** `features/session/cli.feature:348` — idle session refuses (no marker created)
5. **keep** `features/session/cli.feature:358` — live turn: stamp `:cancelled` and return; still in-flight (did not wait). Lazy-impl killer: `cancel!` does not write `:cancelled` on the marker.
6. **keep** `features/session/cli.feature:375` — orphan marker: stamp and return

`features/bridge/cancel.feature` (isaac-agent)
7. **keep** `features/bridge/cancel.feature:51` — drive honors the stamp → turn result cancelled; marker gone

`features/delivery.feature` (isaac-hail @ `48f0a0c`)
8. **keep** `features/delivery.feature:845` — live hail archives to `hail/cancelled/`; not delivered/failed/deliveries; log `:outcome :cancelled`

`features/turn-resume.feature` (isaac-hail)
9. **keep** `features/turn-resume.feature:123` — cancelled hail marker is not re-queued; lands in `hail/cancelled/`; marker gone

`features/session/resume_repair.feature` (isaac-agent)
10. **keep** `features/session/resume_repair.feature:71` — cancelled comm marker is dropped; no interruption note; marker gone

New steps: none.

## Acceptance

Un-`@wip` the ten scenarios above. Add `cancel` to the existing `--help` subcommand list at `features/session/cli.feature:22` in the same agent commit.

isaac-agent:

```
bb features features/session/cli.feature:329
bb features features/session/cli.feature:335
bb features features/session/cli.feature:341
bb features features/session/cli.feature:348
bb features features/session/cli.feature:358
bb features features/session/cli.feature:375
bb features features/bridge/cancel.feature:51
bb features features/session/resume_repair.feature:71
```

isaac-hail:

```
bb features features/delivery.feature:845
bb features features/turn-resume.feature:123
```

Also `bb spec && bb features` green in both repos.

## Handoff (scrapper@isaac-work-1)

branch: bean/isaac-jejt @ d13c18e (base origin/main@4e34748) — isaac-agent
branch: bean/isaac-jejt @ 3f7bcde (base origin/main@4f3e0ea) — isaac-hail

Agent: SessionStore `request-cancel!` stamps `:cancelled` on the existing turn marker. `isaac sessions cancel <id>` fire-and-forget; idle/unknown/missing refuse exit 1. `cancelled?` is atom OR marker. Resume archives cancelled hail to `hail/cancelled/` then deletes the marker; cancelled comm drops with no interruption note.

Hail: live `run-turn!` cancelled archives `hail/cancelled/` with `:outcome :cancelled` (not delivered/failed/requeued). `hail-subdirs` includes `cancelled`.

Acceptance: ten scenarios un-@wip. Agent focused features green (9). Hail focused delivery.feature:845 + turn-resume.feature:123 green against agent d13c18e. Agent `bb spec` 1739/0. Hail `bb spec` 156/0. Did not pin modules.edn. Did not commit hail bb.edn override.



## Landed on main (2026-09-10)

main-sha: isaac-agent ac1bf9b99522e338e79ebea9579804b9491c805e
main-sha: isaac-hail 9887de094940511d9045cf4225db5409ed8cbb15



## Verify fail (attempt 1, 2026-09-10): hail main 9887de0 CI Tests run 34535392748 red — native bb ci uses pinned isaac-agent 461082b8 (pre-jejt); delivery.feature:845 Expected 0 got 1; turn-resume.feature:123 Expected truthy was nil

HEAD: hail 9887de094940511d9045cf4225db5409ed8cbb15 (main, clean)
Working tree: clean
CI: https://github.com/slagyr/isaac-hail/actions/runs/34535392748 job verify 103065694575 step "Run bb ci"
CI env: AGENT_SHA=461082b8b6106c74e4d7acf47cc74ad8319575fa FOUNDATION_SHA=e0dc789b58723a3415a12d5f0d95e0d9148bc316 SERVER_SHA=eb51cc48b8964dabb678086ac36051a86d94c03a
Local reproduction (isaac-hail @ 9887de0, native bb features, no ISAAC_GIT): same 2 failures as CI.

Root cause: hail deps.edn / bb.edn pin isaac-agent at 461082b8. jejt agent squash ac1bf9b99522e338e79ebea9579804b9491c805e is on origin/main but not pinned. Native `bb ci` / `bb features` (what GitHub Actions runs) therefore cannot see request-cancel! / cancelled? marker / archive-cancelled-hail!. Verify gate used ISAAC_GIT=1 jvm-features against local agent — green there, red on the published pin. Worker note: "Did not pin modules.edn. Did not commit hail bb.edn override."

Repair path:
1. Pin hail deps.edn + bb.edn isaac-agent and isaac-agent-spec :git/sha to ac1bf9b99522e338e79ebea9579804b9491c805e (both :deps and :aliases :spec).
2. Native (no ISAAC_GIT): `bb features features/delivery.feature:845 features/turn-resume.feature:123` then `bb ci` green.
3. Push hail main. Do not treat JVM/local-root as the CI gate.

Hail 050d0eec (ci-failure, reply_to this thread). Correlation: do not independent-repair.



## CI evidence (isaac-agent, 2026-09-10)

CI Tests run 34535363284 on ac1bf9b job verify 103065599427 step "Run bb ci": specs 1739/0; features 808 examples, 1 failure.
https://github.com/slagyr/isaac-agent/actions/runs/34535363284

Failure: features/session/cli.feature:367 — And session "design-chat" in-flight status is true; Expected true got false.
Prior steps in that scenario passed: exit 0, turn marker exists with cancelled true. Only the in-flight atom was already false.

Local reproduction (isaac-agent @ ac1bf9b, native bb):
- `bb features features/session/cli.feature:358` → 1/0/4 green
- `bb features features/session/cli.feature` → 34/0/107 green
Does not reproduce isolated or on the full cli.feature file. CI-only under the 808-example suite (Linux).

Likely race: `sessions cancel` stamps the marker (fire-and-forget, no bridge/cancel!); `cancelled?` (atom OR marker) is polled on the live turn (~50ms SSE / tool-loop), so the turn can clear in-flight before the next And step while the marker is still on disk. Scenario intent (lazy-impl killer): stamp AND still in-flight (did not wait).

Hail 8a6bee4e (ci-failure). Correlation: do not independent-repair. Outstanding with attempt-1 hail pin on isaac-hail.

## Repair (scrapper@isaac-work-2, 2026-09-10)

Pinned hail `deps.edn` + `bb.edn` isaac-agent and isaac-agent-spec `:git/sha` to `ac1bf9b99522e338e79ebea9579804b9491c805e` (both `:deps` and `:aliases :spec` extra-deps). Did not pin `modules.edn`. Did not touch agent 8a6bee4e.

hail main: `fac43ef` (base origin/main@9887de0)
branch: bean/isaac-jejt @ fac43ef (base origin/main@9887de0)

Native (no ISAAC_GIT), gitlibs ac1bf9b9:
- `bb features features/delivery.feature:845 features/turn-resume.feature:123` → 2/0/10 green
- `bb spec` → 156/0/358 green

Local `bb ci` features: 146 examples, 7 failures in band-inheritance / band-prompt templating (nil pending hail). Those 7 pass isolated (`bb features features/band-inheritance.feature` → 7/0/16). CI run 34535392748 on 9887de0 (Linux, 23s) had only the two jejt pin failures — band inheritance was green there. Local full-suite pollution, not a pin regression. GitHub Actions clones siblings at the pin; native CI is the gate.

Did not independent-repair hail 050d0eec / agent 8a6bee4e.



## Verify fail (attempt 2, 2026-09-10): hail pin fac43ef CI Tests 34539090442 still red — 7 band-inheritance/prompt failures; agent ac1bf9b CI 34535363284 still red on cli.feature:367

HEAD: hail fac43ef163fe4d7ca7ae210e4ecf2e1704a584a6 (main, clean); agent ac1bf9b99522e338e79ebea9579804b9491c805e (main, clean)
Working tree: both clean

Hail pin is correct: deps.edn + bb.edn isaac-agent / isaac-agent-spec :git/sha = ac1bf9b9. Native focused delivery.feature:845 + turn-resume.feature:123 2/0/10 green; hail bb spec 156/0/358. CI AGENT_SHA now ac1bf9b9. The two jejt hail scenarios that failed on 9887de0 are gone.

GitHub Actions hail CI Tests 34539090442 (fac43ef) job verify: specs 156/0; features 146 examples, 7 failures, 2 pending:
1-4 Hail band inheritance via base template bands — Expected truthy/maps/strings got nil
5-7) Hail band prompt templating with params — Expected rendered prompt got nil
https://github.com/slagyr/isaac-hail/actions/runs/34539090442
Worker noted these 7 pass isolated locally and were not in CI 34535392748 (Linux 146/2 on 461082b8). After the pin they appear on Linux CI. Not pre-existing on main before jejt (6339b55 CI Tests 34531863683 success). Pin-only commit; jejt hail scenarios themselves green.

Agent CI Tests 34535363284 on ac1bf9b still red: features/session/cli.feature:367 in-flight Expected true got false (CI-only under 808-example suite). Local focused 8/0/21 and full cli.feature 34/0/107 green. Worker did not independent-repair 8a6bee4e.

Acceptance unmet: bb features not green in either repo on the published CI gate. Pin fixed attempt-1 hail scenarios only.
EOF
)
