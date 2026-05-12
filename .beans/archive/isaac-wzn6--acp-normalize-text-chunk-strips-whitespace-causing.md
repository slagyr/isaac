---
# isaac-wzn6
title: "ACP normalize-text-chunk strips whitespace, causing concatenated chunks in Toad"
status: completed
type: bug
priority: normal
created_at: 2026-04-29T19:20:23Z
updated_at: 2026-04-29T21:36:40Z
---

## Description

After isaac-juhh enabled streaming for tool-advertised turns, ACP
chunks render concatenated in Toad. Words run together because
ACP strips leading/trailing whitespace from each chunk before
sending to the client.

## Observed

  Icanhelpwithashortexcerpt,butnotrecitethewholepoem...

Each chunk like \"I \", \"can \", \"help \" becomes \"I\", \"can\",
\"help\". Toad receives them as separate agent_message_chunk
notifications and concatenates without separator.

## Root cause

src/isaac/comm/acp.clj:10-14

  (defn- normalize-text-chunk [text]
    (some-> text
            str
            (str/replace #\"^[ \\t]+\" \"\")
            (str/replace #\"[ \\t]+$\" \"\")))

This strips horizontal whitespace from both ends of every chunk.
Pre-streaming this was harmless because each turn emitted ONE
big chunk; now turns emit many small chunks and the strip eats
the inter-word spacing.

## Fix

1. Stop trimming streamed chunk text. Preserve chunks exactly
   as the LLM emitted them. Either delete normalize-text-chunk
   entirely or make it a passthrough (only suppress nil).
2. src/isaac/comm/memory.clj on-text-chunk also trims; remove
   that for symmetry. Update any memory-channel test assertions
   that depend on trimmed text.

## Test strategy

Gherkin tables CANNOT encode trailing/leading whitespace in cell
values (parsers trim cells). So a feature scenario can't directly
assert \"chunk text equals 'Once ' (with trailing space).\" The
verification work splits:

(A) Unit spec — primary regression test.
    spec/isaac/comm/acp_spec.clj: build an AcpChannel, call
    on-text-chunk with literal whitespace-bearing text
    (\"Once \", \" upon\", etc.), assert the emitted JSON-RPC
    notification's params.update.content.text equals the input
    byte-for-byte.

(B) Feature scenario — update existing.
    features/acp/streaming.feature:12-27 currently queues
    [\"Once \" \"upon \" \"a \" \"time...\"] and asserts the
    trimmed values per chunk. That scenario codifies the bug.
    Replace queued chunks with whitespace-neutral content
    (e.g. [\"chunkA\" \"chunkB\" \"chunkC\"]) so the scenario
    no longer bakes in the trim assumption. Whitespace
    preservation lives in the unit spec.

## Definition of done

- normalize-text-chunk no longer trims (or is removed).
- memory channel preserves chunk text exactly.
- Unit spec in spec/isaac/comm/acp_spec.clj covers literal
  whitespace preservation through AcpChannel.
- features/acp/streaming.feature scenario reworked to be
  whitespace-neutral; existing transcript assertion still
  validates concatenation correctness.
- Toad sees properly-spaced multi-word output for streamed turns.
- bb features and bb spec green.

## Related

- isaac-juhh closed; this is the post-streaming fallout.

## Notes

Stopped ACP chunk normalization from trimming streamed text and made memory comm preserve chunk text exactly as emitted. Added ACP and memory comm unit specs for whitespace-bearing chunks, updated ACP and related streaming feature expectations to be whitespace-neutral, restored full crew/model context for the explicit memory-channel feature step, and fixed queued vector parsing in the session feature helpers so padded Gherkin cells still parse as vectors. Verified with bb spec (green) and bb features except for one unrelated failure in features/cli/acp.feature about no-model crew resolution, outside this bead's scope.

