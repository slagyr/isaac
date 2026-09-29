---
# isaac-50zy
title: 'Foreman: transition rows name :actions, always a list'
status: draft
type: feature
priority: normal
created_at: 2026-09-29T15:47:20Z
updated_at: 2026-09-29T15:47:20Z
parent: isaac-q3u3
---

Likely repo: **isaac-foreman**. Decision: Micah, 2026-09-29.

## Why

A transition row's action key is `:action`, and the engine accepts it three ways: missing, a single name, or a list (`machine.clj` `transition-actions`; schema `:action {:type :any}`). Micah: if it's a list, it should be named `:actions`.

## Contract

- Rows use `:actions`, always a vector of action names: `{:start :dark :event :dusk :end :lit :actions [:light-lamp :log-watch]}`. Omit it for a row with no actions.
- **Clean cutover:** `:action` on a row is rejected by `config validate`, naming the key; a non-vector `:actions` is rejected. No alias.
- The machine's top-level `:actions` map (named action definitions) is unchanged; rows' `:actions` refer to it by name. `:states` `:entry`/`:exit` lists are unchanged.
- Existing features (`cli`, `machine`, `turn_action`) move to `:actions`.

## Scenario plan (to draft)

1. A machine whose rows use `:actions` validates and runs its actions in order.
2. A row with `:action` fails `config validate`, naming `action` and the machine.
3. A row whose `:actions` is a single keyword fails validation.

Draft until scenarios exist.
