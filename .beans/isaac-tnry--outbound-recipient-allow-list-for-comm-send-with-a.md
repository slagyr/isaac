---
# isaac-tnry
title: Outbound recipient allow-list for comm_send, with a one-command add
status: in-progress
type: feature
priority: high
created_at: 2026-11-17T02:23:19Z
updated_at: 2026-11-17T02:30:58Z
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

## Contract conflict (2026-11-17)

The baselined `features/tool/comm_send.feature:202` step `And the log has 0 entries matching "Lantern is lit"` has no matching gherclj step definition. After removing only `@wip` and applying the lab patch, the focused run (`bb features features/comm/allow_cli.feature features/tool/comm_send.feature:168 features/tool/comm_send.feature:187 features/tool/comm_send.feature:207`) reports **5 examples, 0 failures, 13 assertions, 1 pending**; the entire refused-and-logged scenario is pending, so the gate's PASS is not evidence that its log/no-body assertions were executed. The existing foundation log steps accept a table (`the log has exactly {n:int} entries matching:`), not this quoted-string step. Per the acceptance instruction, stopped rather than bending product behavior or editing a baselined feature. The config table vector and the two-run CLI scenario do execute and pass. Planner must provide a real assertion step for this exact phrase or change/rebaseline the scenario. Implementation checkpoint is pushed on `isaac-agent` `bean/isaac-tnry` at `b08c32b`; the delivery-worker defense-in-depth and full suites remain to be done after contract repair.

## Planner decision (2026-11-16, for isaac-tnry, undefined log-count step)

My slip: I took `the log has 0 entries matching "…"` for an existing step, and
it is not one. It is a **new step this bean introduces**; implement it. Its
meaning: no captured log entry, printed in full (every key and value),
contains the given text. Put it with the other log steps. The scenario text
stays exactly as baselined, so no re-baseline is needed. Carry on and land
when the gate passes.

## Landed on main (2026-11-17)

Implemented the outbound recipient filter at enqueue and delivery, the non-hosted
`comms allow` CLI, and the full-entry log assertion. Redacted `comm__send`
content from generic tool telemetry so the refused body never reaches the log.
`bb ci`: 2033 specs and 978 features, zero failures (one unrelated pending).
`bb jvm-spec`: 2033 specs, zero failures. `bb bean-gate verify isaac-tnry`
passed on the squash commit.

main-sha: isaac-agent 7ccfb485d45767bfaa556466ed36c9d06156a99d

## Checkpoint (2026-11-17)

Done: green `bb ci`, `bb jvm-spec`, and gate on the squash commit; implementation
committed and pushed to `isaac-agent/main` at the sha above; main-sha recorded.
Next: close the bean as completed and clean up the landed local branch. If
resuming, start at `src/isaac/agent/comm/delivery/worker.clj:52` to inspect
the delivery guard; no implementation changes remain.
