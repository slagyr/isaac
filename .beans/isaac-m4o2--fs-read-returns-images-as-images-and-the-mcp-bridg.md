---
# isaac-m4o2
title: fs__read returns images as images, and the MCP bridge hands them to Claude Code as image content
status: in-progress
type: feature
priority: normal
created_at: 2026-10-03T00:38:18Z
updated_at: 2026-10-03T00:40:09Z
---

Likely repo: **isaac-agent**. Micah + planner, 2026-10-02.

## Why

Crews can't look at an image through Isaac. `fs__read` (`isaac.agent.tool.file`)
slurps text and refuses anything binary ("binary file: …"). The claude-code
provider runs the CLI with `--tools ""`, so the model only has Isaac's tools
over MCP, and the MCP bridge (`isaac.agent.mcp.turns/tools-call`) stringifies
every result into a `{:type "text"}` block. Claude Code passes MCP image
content to the model as an image, so returning one is all it takes there.

## Design

- `fs__read` recognises image files (PNG, JPEG, GIF, WebP; by magic bytes,
  extension as a hint) and returns a structured image result: media type,
  byte size, base64 data, and the path. Other binary files still error.
  Respect a size cap (lean ~5 MB, configurable later) with a clear error
  above it.
- The tool function's result may be an image result as well as text. The
  MCP bridge turns it into `{"type":"image","data":…,"mimeType":…}` with
  `isError false`.
- The transcript records the tool result as a short note,
  `[image: <name>, <media type>, <N> bytes]`, never the base64. Sessions
  stay small, and compaction/recall never see image bytes.
- Providers that build their own requests (Messages API, Responses, grover)
  see only that note for now. Inline images for them are a follow-up bean.
- `fs__write` stays text-only (agreed: models can't produce binary reliably;
  bytes come from exec, fetch, or an image tool).

## Acceptance

- isaac-agent `features/tool/built_in.feature` — "read returns an image file as an image, not text"
- isaac-agent `features/llm/mcp_turn_registry.feature` — "tools/call returns an image file as an MCP image content block"
- isaac-agent `features/llm/mcp_turn_registry.feature` — "the transcript records an image result as a short note, not its bytes"
- "read refuses to dump binary files" (non-image binary) and the rest of both files stay green.
- Deploy check: a claude-code crew asked to describe a PNG on zanebot describes it.

feature-baseline: isaac-agent 6ce4df99970417b8dc99a268b029e98e7e57af50
feature-blob: isaac-agent features/tool/built_in.feature 0fb855c775fb355f61d001a30197176f2d9cb34f 76
feature-blob: isaac-agent features/llm/mcp_turn_registry.feature 867576939ca7c66feebd90a5ba0f9095f6d4c6bd 65
feature-blob: isaac-agent features/llm/mcp_turn_registry.feature 867576939ca7c66feebd90a5ba0f9095f6d4c6bd 82

## Landed on main (2026-10-02)

main-sha: isaac-agent 278d90e3f46f4152d478fcc74183f6cd55777e2c

## Verification

`bb ci` passed (1874 specs, 853 feature examples; one pre-existing pending). `bb bean-gate verify isaac-m4o2` passed against the landed main commit. Live Claude Code deploy check requires a running deployed crew and remains untested in this checkout.
