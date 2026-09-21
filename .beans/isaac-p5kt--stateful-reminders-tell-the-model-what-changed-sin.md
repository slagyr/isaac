---
# isaac-p5kt
title: 'Stateful reminders: tell the model what changed since it last looked'
status: draft
type: feature
priority: normal
created_at: 2026-09-21T14:10:58Z
updated_at: 2026-09-21T14:10:58Z
---

## What

Short, harness-authored notes attached to the conversation when something the
model relies on has **changed** since it last looked. They report state, not
advice: they are not "please batch your tool calls"; they are "the config you
were given has been reloaded".

Every harness we read has these, and all of them use them for state:

- **Claude Code** (seen from inside): date changed, working directory moved,
  tools became available or unavailable, instruction files re-read after
  compaction, mode changed, and a conditional nudge when the todo list goes
  unused.
- **Grok Build** (`xai-grok-shell/src/session/acp_session_impl/`): a
  background task finished, monitor events, skill updates, plan mode, an image
  dropped from a tool result, hook notes.
- **OpenCode** (`packages/opencode/src/session/reminders.ts`): plan/build mode
  switches, appended to the last user message as synthetic parts.

Only one reminder in the three codebases reacts to behavior: Grok Build's
doom-loop recovery (`xai-grok-sampler/src/doom_loop_recovery.rs`), which fires
when a response repeats itself. That is for a failure. None of them coach
efficiency this way.

## Candidates for Isaac (to be decided, not a commitment)

- a config file the session depends on reloaded mid-turn (crew, soul, model).
  Hot-reload (isaac-1pi2) makes this real: today the model is never told
- the session was compacted, and what was kept
- the date changed during a long turn
- a turn resumed after a server restart (turns pause and resume)
- a background command finished
- tools or skills added or removed mid-session

## Principles taken from the three harnesses

1. **Only on change.** A reminder is sent once, when its state changes. Nothing
   repeats every cycle, so there is no token burn to manage and no stripping
   to do. It stays append-only and the prefix cache is never disturbed.
2. **Marked as the harness speaking.** Wrap it in the nonce trusted block.
   Grok Build also escapes the closing tag inside any untrusted content it
   quotes (`escape_reminder_close_tag`), so quoted text cannot end the block
   early. Do the same.
3. **Marked synthetic.** Grok Build tags these `SyntheticReason::SystemReminder`
   so they never count as real user prompts: not for turn counting, not for
   compaction boundaries, not in session titles. Isaac needs the same.
4. **The drive stays generic.** The drive must not know about hails, beans or
   git. Modules contribute reminder sources through a berth (in the manner of
   isaac-bbe0's declarations); the drive only gathers pending reminders and
   hands them over.
5. **Adapters attach them in their own wire shape**, through the existing
   `followup-messages` path: a trailing user message for chat-completions and
   responses; a text block inside the tool_result user message for Anthropic,
   because roles must alternate. No new `api/Api` method is needed (see the
   JVM `AbstractMethodError` note in isaac-5n68's history).

## Relation to isaac-5n68

5n68 handles batching the way the industry does: per-model-family system
prompt text. If that fails for GLM, a behavioral nudge could ride this same
mechanism later. That would be a deliberate exception to "state, not
coaching", and it should be justified by data.

## Status

Draft. Needs a decision on which sources come first, and on the berth shape.
