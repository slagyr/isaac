---
# isaac-50zy
title: 'Foreman: transition rows name :actions, always a list'
status: todo
type: feature
priority: normal
created_at: 2026-09-29T15:47:20Z
updated_at: 2026-09-29T15:54:33Z
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


## Acceptance

Scenarios `@wip` on isaac-foreman main at 74b70f5. Remove `@wip` from this bean's scenarios; all pass:

- [ ] `bb features features/foreman/machine.feature` — `:14`, `:44` (actions fire in order), `:69`, `:93` (`:action` and a non-vector `:actions` rejected)
- [ ] `bb features features/foreman/cli.feature:24` and `:62` (Background moved to `:actions`). **Leave `:47` `@wip`** — it is isaac-qrl1's.
- [ ] `bb features features/foreman/turn_action.feature` — `:35`, `:55`, `:91`, `:124`
- [ ] Schema: rows declare `:actions` (vector of keywords); `:action` rejected by `config validate` naming the key and machine. `checks.clj` and `machine.clj` read `:actions`.
- [ ] `bb verify` green; version bump.

feature-baseline: isaac-foreman 74b70f54700baf7aeea410dfe0f246b11b021642
feature-blob: isaac-foreman features/foreman/machine.feature de6f36319e17886dc95a3ef58907b8f0639e73ee 14,44,69,93
feature-blob: isaac-foreman features/foreman/cli.feature 41152f141d71b7d294ae2a482e6db1bb898ad230 24,62
feature-blob: isaac-foreman features/foreman/turn_action.feature fd589ab3afc864f005264bb74edf041f617e28ca 35,55,91,124
