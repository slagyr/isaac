---
# isaac-f47m
title: web_search config error says :api_key; schema wants :api-key
status: completed
type: bug
priority: normal
tags:
    - agent
    - tools
created_at: 2026-09-15T20:17:16Z
updated_at: 2026-09-15T20:49:09Z
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

## Worker evidence (2026-09-15, scrapper@isaac-work-2)

Implemented on isaac-agent `bean/isaac-f47m` @ `3cefee1` (base `origin/main@3f9fdc2`). The unconfigured web search error now directs operators to `:api-key`; unit and acceptance coverage require `api-key` and reject `api_key`; the authorized scenario is no longer `@wip`.

- `bb spec spec/isaac/tool/web_search_spec.clj`: 4 examples, 0 failures, 14 assertions.
- `ISAAC_GIT=1 bb features features/tool/web_search.feature:52`: 1 example, 0 failures, 4 assertions.
- `bb ci`: all 1628 specs passed (3345 assertions); feature suite had one unrelated transient failure in `features/config/schema_cli_options.feature:45`, which passed immediately in focused rerun (1 example, 0 failures, 4 assertions). The f47m focused feature remained green.



## Landed on main (2026-09-15)

main-sha: isaac-agent cd9042304b6db7cd4a59ed00f6bdcb5b7f5789d3
