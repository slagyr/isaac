---
# isaac-7v06
title: discover! errors on duplicate berth declarations
status: scrapped
type: task
priority: normal
created_at: 2026-06-13T18:37:09Z
updated_at: 2026-06-13T18:39:15Z
---

## Problem (manifest-shape audit, item 1)

Berth declarations are silently first-wins. `collect-berth-declarations` (`module/loader.clj`) just `mapcat`s every `[berth-id berth-decl]` across modules, and `find-berth-decl`/`ordered-berth-decls` take the **first** match. Two modules declaring the same berth-id (e.g. a third-party module redeclaring `:isaac.server/tools` with a different schema) would NOT error at discovery — consumers then validate against whichever declaration won the walk order.

## Risk

Confusing failures, or validation against the wrong schema. Low today (only `:isaac.core` + `:isaac.server` declare berths) but the foundation extraction makes third-party modules the norm, so it grows. The audit lists this as the #1 close-before-extraction item.

## Fix

Detect duplicate berth-ids across modules in `discover!` (or `collect-berth-declarations`) and emit a structured error: "berth `:foo` declared by both `:mod.a` and `:mod.b`". Mirrors how `merge-contributions` (config-schema) and `merge-descriptors` already treat structural disagreement as an error.

## Acceptance

- Two modules declaring the same berth-id produce a located error (not silent first-wins).
- `bb spec` + `bb features` green in isaac and isaac-foundation.

## Related

- Foundation extraction prep (isaac-owrh / isaac-brth). Close before the cut.
- Sibling: config-compose collisions as exceptions (filed alongside).

## Reasons for Scrapping

Filed in error — tracking the audit directly per request, not as beans.
