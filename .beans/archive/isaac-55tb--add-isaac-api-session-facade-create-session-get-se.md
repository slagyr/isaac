---
# isaac-55tb
title: "Add isaac.api.session facade (create-session!, get-session)"
status: completed
type: task
priority: normal
created_at: 2026-05-05T17:35:38Z
updated_at: 2026-05-05T17:48:12Z
---

## Description

Why: Discord-as-module looks up sessions and creates them when needed. It currently :requires isaac.session.storage directly, but modules should pull from the curated isaac.api.* surface (see isaac-5i8v).

## Scope

- isaac.api.session re-exports create-session! and get-session from isaac.session.storage.
- Implementer may choose bare alias (def-style) or a thin wrapper; bare alias is the floor.

Signatures (from src/isaac/session/storage.clj):
- (create-session! state-dir identifier)
- (create-session! state-dir identifier opts)
- (get-session state-dir identifier)

## Out of scope

- Other session.storage fns. Discord only uses these two; expand the facade as future modules surface needs.
- Reshaping the underlying API.

## Acceptance

- isaac.api.session exists and re-exports create-session! and get-session.
- A module can :require [isaac.api.session] in place of [isaac.session.storage] for those call sites.

## Acceptance Criteria

isaac.api.session exists and re-exports create-session! and get-session; a module can :require it in place of isaac.session.storage

