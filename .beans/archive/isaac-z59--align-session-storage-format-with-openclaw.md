---
# isaac-z59
title: "Align session storage format with OpenClaw"
status: completed
type: feature
priority: normal
created_at: 2026-04-10T00:44:40Z
updated_at: 2026-04-10T03:28:44Z
---

## Description

Isaac's session storage format diverges from OpenClaw in several ways that prevent interoperability. Align the format so Isaac sessions can be read by OpenClaw and vice versa.

Changes needed in storage.clj:
1. Index format: array → object keyed by session key
2. Timestamps: epoch ms → ISO 8601 strings
3. Entry IDs: full UUID → 8-char hex
4. Session header: add version (3) and cwd fields
5. Message content: plain strings → content block arrays [{type: text, text: ...}]
6. Assistant messages: store per-turn usage, stopReason, api
7. Index keyed by session key (object not array)

Also migrate existing sessions in ~/.isaac to the new format.

Touches: storage.clj, prompt/builder.clj (read content blocks), context/manager.clj (extract messages from blocks), plus all code that reads message content as strings.

Feature: features/session/storage.feature (10 @wip scenarios)

## Acceptance Criteria

Remove @wip from all 10 scenarios in storage.feature and verify each passes:
  bb features features/session/storage.feature:11
  bb features features/session/storage.feature:25
  bb features features/session/storage.feature:111
  bb features features/session/storage.feature:130
  bb features features/session/storage.feature:144
  bb features features/session/storage.feature:153
  bb features features/session/storage.feature:166
  bb features features/session/storage.feature:182
  bb features features/session/storage.feature:193
  bb features features/session/storage.feature:205

Migrate existing sessions in ~/.isaac to new format.
Full suite: bb features, bb features-slow, and bb spec pass.

