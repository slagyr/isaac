---
# isaac-2tez
title: Coalesced busy-session messages get a second, duplicate turn (drain tick races finish-marking)
status: in-progress
type: bug
priority: high
created_at: 2026-09-30T17:56:19Z
updated_at: 2026-09-30T18:58:38Z
---

Found 2026-09-30 by the gchat restructure (isaac-fstx). Scenario: three quick messages in one DM thread (gchat features, isaac-xoqn) should get ONE consolidated reply; against agent 123d718 they get two.

Trace (worker's): isaac-agent `bridge/core.clj`'s drain-waiting-session cleanup fires a nested `tick!` from inside the FIRST coalesced turn's `dispatch!`, before that turn's `process-record!` has marked the trailing coalesced members `:finished`. Since isaac-e9jl/isaac-2lc4 moved finish-marking after `dispatch!` returns (async worker), the nested tick re-discovers messages 2 and 3 as `:waiting-session` and runs them as a second, spurious turn. Pre-e9jl, finish-marking was inline and the race couldn't happen.

Production impact: any comm (gchat, discord, imessage…) on an agent with e9jl may double-reply to bursts of messages.

## Acceptance
- Red first: an isaac-agent spec or feature that coalesces several waiting messages for one busy session and asserts exactly one turn runs for them (fails on current main).
- Fix: coalesced members are marked finished before any drain tick can see them (or the drain skips members of an in-flight coalesced turn); no sleeps.
- isaac-gchat branch `bean/isaac-fstx` (worktree ../isaac-gchat-isaac-fstx) goes 60/60 on `bb jvm-features` when pinned to the fixed agent.
- Full isaac-agent `bb ci` + `bb jvm-spec` green.

Ungated; planner verifies. Agent main is on the post-restructure namespaces (`isaac.agent.*`).
