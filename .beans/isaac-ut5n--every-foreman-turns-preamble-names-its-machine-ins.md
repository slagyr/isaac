---
# isaac-ut5n
title: Every Foreman turn's preamble names its machine, instance and state
status: completed
type: feature
priority: normal
created_at: 2026-10-05T14:46:30Z
updated_at: 2026-10-05T14:54:47Z
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-05.

## Why

Micah: every run Foreman kicks off should know it is a state-machine turn and
which machine it belongs to. Today only `:output :event` turns name the machine
and instance in their preamble; `{:data …}` turns say only where the reply goes,
and turns with no `:output` get no preamble at all. The turn record's `:origin`
carries the machine and instance, but the model never sees it. A turn that knows
its machine and instance can pull the instance's data with `foreman__data`
(pull, not push), and for bean work the instance id is the bean id.

## Design

- Every turn Foreman submits gets a preamble whose first line is:
  `This turn is part of Foreman machine <machine>, instance <instance> (state <state>).`
  where `<state>` is the state the instance just entered.
- The `:output`-specific line follows it unchanged: `{:data :k}` → reply stored as
  the instance's `k`; `:event` → the valid `event:` lines.
- No change to prompts, origin, or observers.

## Acceptance

- isaac-foreman `features/foreman/turn_preamble.feature` — all three scenarios.
- The rest of the isaac-foreman features stay green (reply_event's preamble scenario included).

feature-baseline: isaac-foreman 0ae7b5ff806d8c75a72670510ae30e7d0ebba3b3
feature-blob: isaac-foreman features/foreman/turn_preamble.feature 65f8e1aba4335f9a23832707e39d27cb36006516

## Landed on main (2026-10-05)

main-sha: isaac-foreman 20d96c4b6b0a8967dc9d63fa3c79d02b1e3dfff8
