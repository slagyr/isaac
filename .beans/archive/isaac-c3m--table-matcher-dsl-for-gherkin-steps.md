---
# isaac-c3m
title: "Table matcher DSL for Gherkin steps"
status: completed
type: task
priority: high
created_at: 2026-03-31T19:48:42Z
updated_at: 2026-03-31T21:49:39Z
---

## Description

Implement the reusable table assertion DSL used across all feature files.

## Location
spec/isaac/features/matchers.clj with unit tests in spec/isaac/features/matchers_spec.clj

## State Convention
When steps store results via gherclj state: (g/assoc! :listing results)
Then steps read :listing and run matchers against it.

## Features (implementation order)
1. Exact value matching (simplest case)
2. Empty cell = null
3. Dot-notation and array indexing for nested access (parent.child, items[0].name)
4. #"regex" pattern matching
5. #index for positional ordering
6. #"regex":name capture and #name reference
7. Key-value vertical table for single object inspection (| key | value |)

## Error Reporting
Full diff on failure: which rows matched, which didn't, and per-cell expected vs actual.

## Gherkin Steps Provided
- the listing has N entries
- the listing has entries matching: (table) — subset, unordered unless #index present
- the <noun> matches: (key-value table) — single object inspection

