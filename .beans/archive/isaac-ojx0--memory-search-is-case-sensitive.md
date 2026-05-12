---
# isaac-ojx0
title: "Memory search is case-sensitive"
status: completed
type: bug
priority: deferred
created_at: 2026-04-29T21:57:09Z
updated_at: 2026-04-30T01:29:48Z
---

## Description

Isaac's memory_search tool is case-sensitive by default. The
LLM almost never wants case-sensitive search — \"orpheus\" should
match a memory entry containing \"Orpheus\".

## Source

src/isaac/tool/memory.clj:91

  (defn- matching-lines [query path]
    (let [pattern (re-pattern query)]
      ...))

re-pattern with no flag prefix builds a case-sensitive regex.

## Spec

features/tools/memory.feature has a new @wip scenario:
\"memory_search is case-insensitive\". Lowercase query, mixed-case
content, result preserves original casing. No new step phrases.

## Fix

One-line change: prepend \"(?i)\" to the regex.

  (re-pattern (str \"(?i)\" query))

Always case-insensitive, no flag. Output preserves original casing
because we re-find against the original line.

## Definition of done

- The new @wip scenario passes
- Existing memory_search scenario still passes
- bb features and bb spec green

## Out of scope

- Beads' own \`bd memories\` command has the same conceptual bug
  (per Zane's original observation). Separate concern; should be
  reported upstream to beads, not fixed here.

