---
# isaac-tnry
title: Outbound recipient allow-list for comm_send, with a one-command add
status: draft
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

Draft until the acceptance scenarios are written against real steps in
`features/tool/comm_send.feature` and baselined.
