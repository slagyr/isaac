---
# isaac-895
title: "Introduce context pattern for threading request state"
status: draft
type: task
priority: deferred
tags:
    - "deferred"
created_at: 2026-04-13T02:59:23Z
updated_at: 2026-04-17T04:29:22Z
---

## Description

## Summary

Many functions pass the same bag of options through multiple layers: state-dir, session-key, model, provider, provider-config, context-window, soul, channel. This is the 'opts map threading' pattern and it's getting unwieldy.

A context pattern (like Ring request maps or Pedestal context) would create a single map that flows through the entire request lifecycle, accumulating state as it goes:

```clj
{:session-key "friday-debug"
 :state-dir   "~/.isaac"
 :agent       {:name "ketch" :soul "..." :model "grok-4-1-fast" :provider "grok"}
 :channel     <AcpChannel>
 :transcript  [...]
 :tools       [...]
 :request     {:messages [...] :model "..."}}
```

Each layer enriches the context rather than passing N individual args. The bridge creates the initial context, the drive consumes it.

## Benefits
- Functions take one arg instead of destructured opts maps
- Easy to add new fields without changing signatures
- Natural place for middleware-like transformations
- Testable — just build a context map

## Defer until
Bridge and drive need to exist first. The context pattern is how they communicate.

## Acceptance Criteria

A unified context map flows through bridge → drive → channel. Functions take context, not opts bags.

