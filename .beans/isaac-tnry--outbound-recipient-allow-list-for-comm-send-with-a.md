---
# isaac-tnry
title: Outbound recipient allow-list for comm_send, with a one-command add
status: todo
type: feature
priority: high
created_at: 2026-11-17T02:23:19Z
updated_at: 2026-11-17T02:23:19Z
---

Found by Zane Lab, 2026-11-16 (security finding
`outbound-sends-have-no-recipient-allowlist`, experiment `outbound-allowlist`).
Micah's condition: "As long as it's easy for me to update the allow list."

## What is wrong

Nothing checks where an outbound message goes. `comm_send` (isaac-agent
`tool/comm_send.clj`) builds the record and enqueues it; the delivery worker
calls the comm's `send!`. The `allow-from` lists in iMessage and Discord
filter inbound only. A crew talked into it by a web page, an inbound message
or a file can send anything to anyone. In the last 7 days every send went to
Micah, but only because the model chose to.

## Design (from the lab's proven patch)

- `:comm/allow-to` on any comm slot. Missing: no filter (today's behaviour).
  A list: only those targets. Empty: refuse everything.
- `comm_send` refuses before enqueueing: a tool error
  `comm <slot> refused target <t>: not on the outbound allow-list`, and a
  warn line `:comm/outbound-refused` with comm and target, never the body.
- One short command adds a recipient: `isaac comms allow <slot> <target>`
  (validated, written to the slot's config file, hot-reloaded). With no
  target it lists the slot's allow-list. Agents cannot run it through `/cli`.

## Reference

Zane's patch (+261/−17, 7 files, 12 new specs; isaac-agent at `3233755`) and
draft acceptance are on zanebot in
`/Users/zane/agents/zane-lab/experiments/outbound-allowlist/` (`BEAN.md`,
`isaac-agent.patch`). Lab results: specs 2030/0, features 973/0.

## Not in this bean

- Discord accepts any channel-id-shaped target even when channels are
  configured (`resolve-target-channel`); the allow-list covers it, but the
  docstring/behaviour mismatch deserves its own fix.
- Zane's `send-email` script takes any recipient and allows header
  injection; the lab has a patched copy (config change, Micah approves).
- After deploy: set `comm/allow-to` on zanebot's iMessage and Discord slots.


## Acceptance

- The five `@wip` scenarios baselined below pass with `@wip` removed: three in
  `features/tool/comm_send.feature` (allowed target enqueued; target off the
  list refused, logged as `:comm/outbound-refused` with comm and target and
  never the body; empty list refuses everything) and two in the new
  `features/comm/allow_cli.feature` (`isaac comms allow <slot> <target>` adds
  and reports `<slot> allows <target>`; with no target it lists the slot's
  allow-list; an unknown slot exits 1 and names it).
- A slot with no `:comm/allow-to` behaves exactly as today: every existing
  comm_send scenario still passes unchanged.
- The refusal happens in `comm_send` before enqueueing, and the delivery
  worker refuses too (a record already in the queue for a target off the list
  is not sent).
- `comms allow` is not runnable through `/cli` by an agent (not `:hosted`).
- `bb ci` and `bb jvm-spec` pass. No version bump; that happens at deploy.
- Start from Zane's patch on zanebot
  (`/Users/zane/agents/zane-lab/experiments/outbound-allowlist/isaac-agent.patch`,
  against `3233755`); main has moved, rebase by hand. You own the result.
- If a baselined step cannot do what a scenario asks of it (the vector value
  in the `config:` table, or the two-run CLI scenario), stop and say which
  step, do not bend the production code to fit.

feature-baseline: isaac-agent bb89bf66a2da4865932a64073aa6265be80b1830
feature-blob: isaac-agent features/tool/comm_send.feature f074391e55d37dac35d748077a057306d48cade8 168,187,207
feature-blob: isaac-agent features/comm/allow_cli.feature 57760c887dbcce53ac4d5148c1c50d7fbacdde42 14,32
