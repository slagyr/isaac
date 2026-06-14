---
# isaac-6m4j
title: Config-compose collisions surface as exceptions, not located error rows
status: scrapped
type: bug
priority: normal
created_at: 2026-06-13T18:37:09Z
updated_at: 2026-06-13T18:39:16Z
---

## Problem (manifest-shape audit, item 3)

Some config-berth collisions `throw` during `load-config` instead of becoming `{:errors [...]}` rows like every other manifest problem, so `isaac config validate` blows up with a stack trace rather than reporting a located error.

Still throwing after isaac-un18:
- `schema-compose/merge-descriptors` — table **shell** disagreement (two modules redefining a table's structure). Thrown through `cache-composed!`. (un18 deliberately keeps this an error — but it should be a row, not an exception.)
- `check-compose/merge-contributions` — duplicate check id, missing `:fn`, `:fn` that doesn't resolve to a fn.
- `schema-compose/inline-schema` — invalid inline `:schema` (meta-conform failure).

(isaac-un18 already converted the common case — per-entity config-schema collisions — to `:<kind>/override` warnings, so those no longer throw. This bean is the residual structural/check throws.)

## Fix

Catch these in `load-config-result` (or normalize at the compose boundary) into `{:key <module/config-key path> :value <message>}` rows, so `config validate` reports them located and keeps going.

## Acceptance

- A shell collision, a duplicate check id, and an invalid inline schema each surface as a located error row in `(load-config-result ...) :errors` — `config validate` does not throw.
- `bb spec` + `bb features` green in isaac and isaac-foundation.

## Related

- Foundation extraction prep (isaac-owrh / isaac-brth). Close before the cut.
- Sibling: duplicate berth declarations (filed alongside).

## Reasons for Scrapping

Filed in error — tracking the audit directly per request, not as beans.
