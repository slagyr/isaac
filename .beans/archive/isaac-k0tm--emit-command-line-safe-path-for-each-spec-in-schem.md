---
# isaac-k0tm
title: "Emit command-line-safe path for each spec in schema.term output"
status: completed
type: feature
priority: low
created_at: 2026-04-19T02:39:18Z
updated_at: 2026-04-20T02:04:54Z
---

## Description

isaac.config.schema.term renders schemas as terminal-friendly text (stolen from c3kit.apron.schema.term in 47b713c for this purpose). Add an annotation next to each field showing the exact path the user can type to reference it via isaac config get or isaac config schema.

## Example output (current)
```
providers
──────────
  api-key    string
             API key
  base-url   string
             API base URL
  ...
```

## Example output (target)
```
providers
──────────
  api-key    string                                 providers._.api-key
             API key
  base-url   string                                 providers._.base-url
             API base URL
  ...
```

The path uses shell-safe grammar (_ as wildcard sentinel, . or / as separators per isaac-soyn / the path convention). Power-user wildcards like [*] remain available but this output uses the friendlier form.

## Scope
- Augment field-block in src/isaac/config/schema/term.clj to render a third column (or right-aligned header) with the path
- Path generation: walk the schema from root, tracking segments; use _ for :value-spec contexts, index for :seq contexts
- Toggle via render option (e.g. :paths? true, default true)
- Test in spec/isaac/config/schema/term_spec.clj

## Depends on
- isaac-9eht (schema command must exist to exercise this)
- isaac-soyn (shell-safe path grammar must be finalized, since rendered paths must match what get/schema accept)

## Motivation
Agent crew (or humans) editing config needs to know exactly how to reference a field they see in the schema — no guessing. Showing the path inline is self-teaching documentation.

## Acceptance Criteria

1. spec->term supports a :paths? option (default true) that adds the path-safe reference alongside each field
2. Paths use shell-safe grammar (_ wildcards, dot separator)
3. Paths shown in schema output are valid arguments to isaac config get and isaac config schema
4. bb spec passes
5. bb features passes

