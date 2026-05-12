---
# isaac-nyuv
title: "Domain-events Comm refactor + prompt CLI UX"
status: completed
type: feature
priority: normal
created_at: 2026-04-29T00:22:48Z
updated_at: 2026-04-29T03:37:09Z
---

## Description

Refactor the Comm protocol from UX hints (on-text-chunk,
on-thought-chunk) toward domain events (on-compaction-start,
on-compaction-failure, etc.) with structured payloads. Drive the
refactor via concrete prompt-CLI features that each existing event
shape can't easily express.

Spec: features/cli/prompt.feature has four @wip scenarios:

1. "prompt shows compaction lifecycle on stderr"
2. "prompt shows tool calls and results on stderr with kind icons"
3. "prompt shows compaction failure inline with the underlying error"
4. "prompt shows a banner when compaction surrenders after repeated
   failures"

## Protocol changes

src/isaac/comm.clj — replace on-thought-chunk with explicit domain
events; keep lifecycle/tool methods that already work:

  (defprotocol Comm
    (on-turn-start          [ch session-key input])
    (on-turn-end            [ch session-key result])
    (on-text-chunk          [ch session-key text])
    (on-tool-call           [ch session-key tool-call])
    (on-tool-cancel         [ch session-key tool-call])
    (on-tool-result         [ch session-key tool-call result])
    (on-compaction-start    [ch session-key {:keys [provider model
                                                    total-tokens
                                                    context-window]}])
    (on-compaction-success  [ch session-key {:keys [summary
                                                    tokens-saved
                                                    duration-ms]}])
    (on-compaction-failure  [ch session-key {:keys [error
                                                    consecutive-failures]}])
    (on-compaction-disabled [ch session-key {:keys [reason]}])
    (on-error               [ch session-key error]))

## Engine changes

src/isaac/drive/turn.clj perform-compaction!
  - Replace (comm/on-thought-chunk channel "compacting...") with
    (comm/on-compaction-start channel key-str payload)
  - On success: on-compaction-success with tokens-saved + duration
  - On failure: on-compaction-failure with the error and current
    consecutive-failures count
  - On surrender: on-compaction-disabled with :reason
    :too-many-failures

## Comm impl changes

- src/isaac/cli/prompt.clj CollectorChannel
    - Render lifecycle events to stderr with the playful glyphs:
      🥬 compacting…, ✨ compacted, 🥀 compaction failed,
      🪦 compaction disabled
    - Render tool calls/results to stderr with kind icons
      (🔍 grep, 📖 read, ✏️ write, ⚙️ exec, 🌐 web_fetch, 💾 memory_*)
    - Stdout stays clean: only the assistant's response text
- src/isaac/comm/acp.clj
    - on-compaction-* → emit agent_thought_chunk for now (matches
      current behavior); future work can pick a richer ACP shape
- src/isaac/comm/null.clj — add no-op impls for the new events
- src/isaac/comm/discord.clj — no-op for now (could surface
  failure/disable as a thread reply later)
- src/isaac/comm/memory.clj — record events with their payloads so
  test assertions can match on structured facts

## Protocol-completeness check

Add a spec that asserts every Comm impl in src/isaac/comm/ implements
every method of the protocol. Catches the next addition's miss at
build time. (Closes the gap that left CollectorChannel without
on-thought-chunk; isaac-x34t merges into this work.)

## Definition of done

- features/cli/prompt.feature scenarios 1-4 pass without @wip
- features/acp/compaction_notification.feature still passes
  (agent_thought_chunk emitted, just from the new event)
- Protocol-completeness spec covers all comm impls
- bb spec and bb features green
- bb match-step works for the new event names (sanity check that
  no scenario invents un-defined steps)

## Closes / supersedes

- isaac-x34t (CollectorChannel missing methods) — solved as a side
  effect; close on merge
- isaac-z6bz (deferred design review) — close, this is the design

## Out of scope (follow-up beads)

- Turn-end receipt: [ model · tokens · cost · duration ]
- Mood-driven failure copy ("the lettuce has wilted: …")
- Discord on-compaction-* surfacing

## Notes

Implemented Comm domain-event refactor for compaction lifecycle, updated ACP/memory/null/discord/prompt channel implementations, added prompt CLI stderr UX for compaction and tool events, added protocol-completeness coverage, and removed @wip from the four prompt feature scenarios. Verified with bb spec, bb features, features/acp/compaction_notification.feature, features/cli/prompt.feature, and bb match-step "the stderr matches:". No unrelated failures remain.

