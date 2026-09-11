---
# isaac-81bd
title: 'Design: proper layering for markdown/preformatted wrapping on human outputs (status, tool results, slash replies)'
status: draft
type: task
priority: normal
tags:
    - design
    - cli
    - comm
    - rendering
created_at: 2026-07-03T15:15:01Z
updated_at: 2026-07-03T15:15:01Z
---

## Problem
`format-status` (and potentially other formatters for tool output, command results, logs, etc.) was emitting ```text ... ``` markdown fences. This was convenient when the primary consumer was a Markdown-capable chat UI or Discord.

Now that we have:
- Direct CLI usage (`isaac sessions show`, etc.) which should be raw text
- Tool results (`session_info`) consumed by the LLM
- Multiple comms (ACP/Toad, Discord, iMessage, etc.)
- Slash command replies

The wrapping decision is in the wrong layer.

## Goals
- CLI and raw/LLM paths: always plain text, no markup.
- Human-facing MD renderers (chat transcript, Discord messages): preformatted blocks should be wrapped in ```text (or equivalent) to preserve alignment and whitespace.
- No double-wrapping, no leakage of presentation concerns into core formatting.

## Proposed architecture

1. **Core layer** (bridge/status.clj, tool implementations, etc.)
   - Produce semantically clean plain text (or data structures).
   - format-status → plain "Session Status\n──────\nCrew ..."

2. **Presentation / reply layer** (bridge/core.clj reply-result, turn result handling)
   - Know when something is "final user-visible output from a slash or explicit command".
   - Tag results when helpful: {:content "...", :preformatted? true, :kind :status}
   - Do **not** hardcode MD here.

3. **Comm layer** (the right place)
   - Each comm knows its client's rendering model:
     - CLI comm: raw.
     - ACP comm: send as text content. Wrap in ```text if the client benefits for blocks like /status.
     - Discord comm: wrap preformatted human replies.
   - Hook points: on-text-chunk / on-turn-end for slash replies; on-tool-result for display-oriented results.

4. **Detection**
   - Mark at source (slash status results, certain tool outputs).
   - Or convention in comms for known block outputs.

## Acceptance criteria
- CLI sessions show (and similar) produce clean raw text.
- Human chat/Discord views get properly wrapped preformatted blocks where it improves readability.
- LLM tool results stay clean (JSON where possible).
- ACP /status works sensibly (decide wrapping based on Toad experience).
- No wrapping in core formatters.
- Clear layering documented.

## Files likely touched
- bridge/status.clj (plain)
- bridge/core.clj (optional tagging)
- comm/* (wrapping decisions)
- acp, discord, etc.
- tests, docs

## Open questions
- Shared util for wrapping?
- How to propagate "this is a block" vs normal text?
- Different treatment for different output kinds (status vs diff vs log)?
