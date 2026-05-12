---
# isaac-4ij3
title: "Tighten tool-calls decoder in session.transcript"
status: completed
type: task
priority: low
created_at: 2026-05-07T17:55:37Z
updated_at: 2026-05-07T23:43:39Z
---

## Description

Two smells in `tool-calls` (currently isaac.message.content/tool-calls; lands at isaac.session.transcript/tool-calls after isaac-15p4):

1. **First-block-only read** (branch 2):
   When :content is a vector and the first block is a toolCall, the function returns the entire vector as-is. If a future message stores multiple toolCalls plus other block types interleaved, this branch silently truncates by predicate-on-first only. Should iterate: filter every block whose `:type` is "toolCall" and return that.

2. **JSON-string heuristic** (branch 3):
   `(str/starts-with? content "[")` then `json/parse-string` to detect toolCall arrays serialized as strings. Fragile — a legitimate user message starting with `[` triggers a parse attempt (the try/catch swallows it cleanly, but it's still doing wrong-shaped work).

   Investigation step before fixing: grep session.storage and the LLM API translation code for places that produce string-encoded toolCall arrays. If no current code path produces that shape, delete branch 3 entirely. If something does, fix at the source — never store toolCalls as JSON strings on the transcript.

Out of scope: `content->text`'s nil-on-unknown-shape behavior. Callers handle nil today; changing that is a separate decision.

## Acceptance Criteria

bb spec green; bb features green; tool-calls returns ALL toolCall blocks when content is a vector with multiple toolCall entries (add spec); branch 3 either deleted (if no caller produces JSON-string toolCalls) or kept with a comment pointing to the storage code that produces the shape; first-tool-call still works.

## Design

The decoder shouldn't paper over storage bugs. If branch 3 is currently dead, removing it eliminates the heuristic AND prevents future regressions where someone re-introduces JSON-string storage and the decoder silently 'works'. If branch 3 is currently load-bearing, fixing storage is the better lever — the transcript should hold structured data, not JSON strings inside strings.

