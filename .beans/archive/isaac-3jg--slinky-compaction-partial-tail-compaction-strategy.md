---
# isaac-3jg
title: "Slinky compaction: partial tail compaction strategy"
status: completed
type: feature
priority: high
created_at: 2026-04-16T15:25:14Z
updated_at: 2026-04-16T21:40:38Z
---

## Description

Replace the current rubberband compaction (compact everything, block the turn) with a slinky strategy that compacts only the oldest portion of the transcript while preserving recent context.

Sessions define a compaction strategy (:rubberband or :slinky) with configurable threshold and tail size. The strategy is a property of the session.

Constants drive the defaults:
- LARGE_TURN_TOKENS (40K) — headroom for tool-heavy turns
- LARGE_FRONTMATTER_TOKENS (10K) — soul + agents + tools
- RECENT_TOPIC_TOKENS (100K) — recent context preserved

Key properties:
- Partial: only the oldest messages are summarized
- Recent context stays intact — no abrupt forgetting
- Configurable per session with sensible defaults from window size
- :rubberband preserved as default behavior

Feature: features/session/compaction_strategies.feature
Pseudocode: src/isaac/session/compaction.clj

## Design

Strategy schemas with c3kit.apron.schema. Default threshold = max(window - 50K, 80%). Default tail = max(window - 150K, 70%). Compaction engine reads strategy from session entry. Slinky compacts messages whose cumulative tokens fall in the tail slice. Async is a separate bead.

## Notes

Implementation starting point: src/isaac/session/compaction.clj (committed with pseudocode). Uses c3kit.apron.schema to define strategy config structure — this is the first use of schema-driven config validation in Isaac and should establish the pattern for future config structures.

