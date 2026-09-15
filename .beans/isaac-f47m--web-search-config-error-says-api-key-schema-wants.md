---
# isaac-f47m
title: web_search config error says :api_key; schema wants :api-key
status: todo
type: bug
priority: normal
tags:
    - agent
    - tools
created_at: 2026-09-15T20:17:16Z
updated_at: 2026-09-15T20:17:16Z
---

The unconfigured `web_search` error tells the operator to set `:api_key`. Schema, `config set`, and `config schema tools.web_search.api-key` all use kebab `:api-key`. Following the error produces `unrecognized segment: api_key`.

## Decision

- Decision (2026-09-15, Micah): the error (and its tests) say `:api-key`. Clean cutover — no snake_case alias.

## Scope (isaac-agent)

- `src/isaac/tool/web_search.clj` `web-search-config-error`
- `spec/isaac/tool/web_search_spec.clj` (asserts `api_key` today)

## Scenarios

`features/tool/web_search.feature` @ 4034d2a

- `:52` web_search without configured API key returns a config error — **edit @wip**: contains `api-key`, does not contain `api_key`

New steps invented: none.

At landing: remove `@wip` from `:52`.

## Exceptions

Authorized (2026-09-15): change the unconfigured-key Then from `api_key` to `api-key` and add the negative `api_key` assertion.

## Acceptance

```
cd isaac-agent
ISAAC_GIT=1 bb features features/tool/web_search.feature:52
bb spec spec/isaac/tool/web_search_spec.clj
bb ci
```
