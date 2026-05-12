---
# isaac-d00u
title: "Add isaac.api.turn facade (run-turn!)"
status: completed
type: task
priority: normal
created_at: 2026-05-05T17:34:41Z
updated_at: 2026-05-05T17:48:13Z
---

## Description

Why: Discord-as-module needs to drive a turn from inbound messages. It currently :requires isaac.drive.turn directly, but modules should pull from the curated isaac.api.* surface (see isaac-5i8v).

## Scope

- isaac.api.turn re-exports run-turn! from isaac.drive.turn.
- Implementer may choose bare alias (def run-turn! impl/run-turn!) or a thin wrapper if a normalized contract makes sense; bare alias is the floor.

## Out of scope

- Design changes to run-turn! itself.
- Other turn-related fns (run-turn! is the only one Discord uses today; expand the facade as future modules surface needs).

## Acceptance

- isaac.api.turn namespace exists and exposes run-turn!.
- A module can :require [isaac.api.turn] in place of [isaac.drive.turn] for that one call site.

## Acceptance Criteria

isaac.api.turn exists and re-exports run-turn!; a module can :require it in place of isaac.drive.turn

