---
# isaac-oecl
title: "Config schema: shallow rendering, absolute paths, map-value-spec format"
status: completed
type: task
priority: normal
created_at: 2026-04-21T01:37:41Z
updated_at: 2026-04-21T01:53:01Z
---

## Description

The isaac config schema command currently prints every named sub-spec (multiple sections), renders relative paths starting with '_', and gives ambiguous titles. Changes: (1) Always shallow — drop the --all deep-expansion. (2) Absolute paths: title becomes '<path> [(entity name)] schema' and per-field path column shows 'crew._.id' not '_.id'. (3) Pick a different color for path annotations. (4) For map-with-value-spec, emit a new layout: '<path> (map) schema' title, description line, then 'key <type> <path>' and 'value <type> <path>' rows styled so they're clearly not field names.

