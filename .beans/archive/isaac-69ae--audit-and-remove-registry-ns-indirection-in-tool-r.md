---
# isaac-69ae
title: "Audit and remove registry-ns indirection in tool registration"
status: completed
type: task
priority: low
created_at: 2026-05-08T00:27:53Z
updated_at: 2026-05-08T15:16:53Z
---

## Description

isaac.tool.builtin/register-all! takes a `registry-ns` parameter — the register fn to call for each spec:

```clojure
(defn register-all!
  ([registry-ns]
   (register-all! registry-ns ::all))
  ([registry-ns allowed-tools]
   ...
   (registry-ns spec) ;; calls whatever fn was passed in
   ...))
```

In production, the only caller passes isaac.tool.registry/register!. The indirection allows alternate registries (testing, custom dispatchers) but we should verify the flexibility is actually used.

## Investigate

1. grep all callers of register-all! across src and spec.
2. Confirm every caller passes `tool-registry/register!` (or some equivalent that's a thin wrapper).
3. Check whether any test ever uses a non-registry register fn.

## Likely outcome

If the indirection is dead flexibility, replace with a direct call:

```clojure
(defn register-all!
  ([] (register-all! ::all))
  ([allowed-tools]
   ...
   (tool-registry/register! spec)
   ...))
```

The signature simplifies; the require already exists; one less plumbing parameter.

If a test or auxiliary path actually uses a different register fn, document why and leave the parameter in place but tighten the doc.

## Why bother

The codebase tends to prefer concrete wiring over speculative indirection (per recent isaac.api / Api protocol cleanup). This is a small consistency improvement that removes one un-earned degree of freedom.

## Out of scope

- Restructuring how the registry itself dispatches.
- Changing the registry's public API.

## Acceptance Criteria

Investigation completed and documented (in commit message or bead notes); if indirection unused, register-all! signature simplifies and tool-registry/register! is called directly; bb spec green; bb features green.

