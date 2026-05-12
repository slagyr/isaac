---
# isaac-iop
title: "Nil tool results stored silently in transcript"
status: completed
type: bug
priority: high
created_at: 2026-04-09T01:24:52Z
updated_at: 2026-04-09T01:40:08Z
---

## Description

When a tool handler returns nil, the result flows through three layers without detection and is stored as {:role toolResult :content nil} in the transcript. No error flag is set, no log warning is emitted. This corrupts the transcript and causes the LLM to receive an empty tool result with no indication of failure.

## Feature
features/tools/execution.feature — 'Nil tool result is treated as an error'

## Root cause
- registry/execute wraps nil as {:result nil} (src/isaac/tool/registry.clj ~line 48)
- tool-fn destructures :result -> returns nil (registry.clj ~line 62)
- run-tool-calls! stores :content nil without checking for nil (src/isaac/cli/chat.clj ~line 182)

## Fix
- registry/execute should detect nil and return {:isError true :error 'tool returned nil'}
- Add defgiven step for registering a nil-returning tool in spec/isaac/features/steps/tools.clj
- Add defthen step 'the tool result should indicate an error'
- Remove @wip tag from the feature scenario once passing
- Log a warning when nil is detected

## Definition of Done
- @wip removed from execution.feature nil scenario
- bb features passes
- bb spec passes

