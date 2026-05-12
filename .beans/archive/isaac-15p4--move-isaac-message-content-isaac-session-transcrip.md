---
# isaac-15p4
title: "Move isaac.message.content → isaac.session.transcript"
status: completed
type: task
priority: normal
created_at: 2026-05-07T17:54:58Z
updated_at: 2026-05-07T19:19:25Z
---

## Description

isaac.message.content is the lone occupant of src/isaac/message/ and decodes the shape of session-transcript message content. Its production callers (isaac.acp.server, isaac.prompt.builder, isaac.context.manager — soon isaac.session.compaction) all operate on transcripts. Transcripts are session-owned (session.storage reads/writes them, session.compaction rewrites them). The current home gives no anchor — "message" by itself is foggy and isaac.message/ is a folder of one.

Moving to isaac.session.transcript names the actual abstraction, joins the populated isaac.session.* neighborhood, and gives future transcript-shape utilities (entry-type predicates, the firstKeptEntryId walk currently inside compaction, splice helpers currently inside session.storage) a natural home.

Changes:

1. File move + ns rename:
   - src/isaac/message/content.clj      → src/isaac/session/transcript.clj
   - spec/isaac/message/content_spec.clj → spec/isaac/session/transcript_spec.clj
   The ns becomes `isaac.session.transcript`. Public surface stays the same: content->text, tool-calls, first-tool-call.

2. Update production callers (3 sites):
   - src/isaac/acp/server.clj
   - src/isaac/prompt/builder.clj
   - src/isaac/context/manager.clj  (or src/isaac/session/compaction.clj if isaac-yfb4 has landed)

3. Remove src/isaac/message/ and spec/isaac/message/ directories.

No behavior change. Only naming.

## Acceptance Criteria

bb spec green; bb features green; grep -rn 'isaac\.message\.content' src spec returns nothing; src/isaac/message/ and spec/isaac/message/ removed; isaac.session.transcript holds content->text, tool-calls, first-tool-call.

## Design

Considered isaac.transcript (new top-level), isaac.transcript.message (sub-ns with headroom), and keeping isaac.message but fleshing it out. Picked isaac.session.transcript: transcripts are session-owned data, session/ is already populated, and a top-level for one file is overkill. Sub-namespace later only if it grows.

