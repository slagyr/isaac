---
# isaac-mve9
title: A delivery lands in the transcript of the session that owns its channel
status: completed
type: feature
priority: high
created_at: 2026-10-06T20:02:26Z
updated_at: 2026-10-06T20:28:10Z
---

Micah, 2026-10-06. A delivery queue only posts. A cron job, attention notice, or another session's `comm__send` that posts into a channel where a crew talks with someone never reaches that channel's session, so the crew answers out of context. Seen on yopp: a queued delivery DM'd micah@marigold.example as yopp, Micah replied "I got it.", and Yopp read it as a reply to something else. Chat did push the post back, but gchat drops its own echoes (`:gchat/message-dropped :reason :self`), by design. The same gap hits red-alert's iMessage pings on zanebot.

## Decision (Micah 2026-10-06)
Record at delivery time, in agent (the delivery side is the only place that knows who wrote it). gchat keeps dropping self echoes.
- Inbound records the channel on the session: `:channels`, a set of `"<comm>:<channel>"` strings (system-managed), written by the comm when a message arrives.
- A comm's send result reports `:channel`, the channel it actually posted to (gchat: the resolved `spaces/…`, including an email resolved to its DM).
- After a successful send, the delivery worker finds the session whose `:channels` holds `"<comm>:<channel>"` and appends an **assistant** message: `[sent here by crew <crew> from session <session>] <content>`.
- Skip when the delivery's `:session` is that session (its own reply is already there), when no session owns the channel, or when the send failed.

## Acceptance (gated)
- The 4 @wip scenarios in isaac-agent `features/comm/delivery/channel_continuity.feature` and the @wip scenario at the end of isaac-gchat `features/comm/gchat/outbound.feature` pass with @wip removed.
- `the following sessions exist:` gains a `channels` column (step change, not new step text).
- Handbook (agent delivery/sessions, gchat outbound) documents the note and `:channels`.
- isaac-gchat pins the agent sha carrying this; pins coherent.
- `bb ci` and `bb jvm-spec` green in isaac-agent; `bb ci` green in isaac-gchat.
- Follow-ups (separate beans): Discord and iMessage record `:channels` on inbound and report `:channel` on send.

Likely repo scope: isaac-agent, isaac-gchat.

feature-baseline: isaac-agent 7d57b3e145a95469019e7cae6c410fe164f19258
feature-baseline: isaac-gchat 99d85d5ebc8ae8a46329c9c34cb5ac8c909a0c52
feature-blob: isaac-agent features/comm/delivery/channel_continuity.feature 492199d49abdbfbf7feca1b71f0d3088f82e9a6f
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 4657a9caa502095f027e9bb3666f8e1f3d308ba9

## Contract conflict (2026-10-06)

Agent implementation and four acceptance scenarios green (`bb features features/comm/delivery/channel_continuity.feature`: 4 examples, 0 failures; `bb jvm-spec`: 1887 examples, 0 failures). Agent native `bb ci` currently hits an unrelated pre-existing native-only failure in `session feature steps a parked send that completes during admission does not await the running turn` (`isaac.foundation.fs/instance: no filesystem available`), reproducible on the focused spec; JVM run is green.

Gchat implementation is on `bean/isaac-mve9` at 9fdb956. Its frozen outbound scenario cannot pass as written: `Then an outbound HTTP request to "https://chat.googleapis.com/v1/spaces/DMM/messages" matches` with `body.text | Your weekly digest.` selects index 0 (`Here.` from inbound turn reply), not index 1 (the delivery). Focused `bb features features/comm/gchat/outbound.feature` reports `Expected "Your weekly digest.", got: "Here."` at outbound.feature:656. Please adjust that scenario to specify `#index | 1` in the table, rebaseline, and hand back. No feature text other than @wip tags was changed by worker. Agent worktree `../isaac-agent-isaac-mve9`, gchat worktree `../isaac-gchat-isaac-mve9`.

## Worker checkpoint (2026-10-06, resumed)

Done: agent landed on main c00aff6 (agent bb ci intermittently red on the documented pre-existing native fs/instance spec; bb jvm-spec 1887/0; agent features 875/0). Gchat branch rebased onto planner main 6416409, frozen scenario unchanged except @wip removed. Gate PASS with both worktrees. Gchat agent/foundation pins updated to coherent landed shas; not committed yet because gchat feature remains red. Next: investigate gchat outbound feature transcript matcher at features/comm/gchat/outbound.feature:660. Delivery appends exact marked note (instrumented MemorySessionStore), but matcher reports Row 2 got Here.; determine whether matcher regex selection or store visibility causes this; run `bb features features/comm/gchat/outbound.feature`, then `bb ci`, gate and land gchat. Do not edit frozen scenario or session_steps_spec.clj:71.

feature-baseline: isaac-agent 7d57b3e145a95469019e7cae6c410fe164f19258
feature-baseline: isaac-gchat 64164092edd404a283925e6b10a70689007163b8
feature-blob: isaac-agent features/comm/delivery/channel_continuity.feature 492199d49abdbfbf7feca1b71f0d3088f82e9a6f
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 842feada4f2db0e2cb0fb0d8b15b43aeab2c01f0

## Planner adjustment (2026-10-06, prowl@isaac-plan)

The digest is the second post. The inbound reply posts first.

`outbound.feature` now selects `#index` 1 for the spaces/DMM delivery assertion. Body text stays `Your weekly digest.` `@wip` stays.

On gchat main `6416409`. Outbound blob `842feada`, line-less. Agent continuity blob `492199d4` is unchanged, re-stated on agent `7d57b3e`.

Rebase onto `6416409`. Drop `@wip` only. Do not edit frozen scenario text. The native `fs/instance` failure in `session_steps_spec.clj:71` is pre-existing and not this bean. Do not edit that spec here.

## Landed on main (2026-10-06)

main-sha: isaac-agent c00aff6e0eca08d7ad87880daa6f1c86495ede8c
main-sha: isaac-agent 25a2fef858d2d0645d1f1bc69060a60720a59fb5
main-sha: isaac-gchat e3dcbefa15af67627d9a9329019bcdf422d74519

The second agent main-sha fixes the shared feature tick nexus so the gchat end-to-end scenario sees the marked note. Gchat pins that agent sha; foundation pins align with it. Agent native bb ci has the pre-existing intermittent session_steps_spec.clj:71 fs/instance failure; bb jvm-spec 1887/0, agent feature 875/0, gchat bb ci 206/0 + 68/0, gate PASS.
