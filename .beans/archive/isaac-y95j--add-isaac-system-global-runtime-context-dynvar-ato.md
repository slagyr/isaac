---
# isaac-y95j
title: "Add isaac.system: global runtime context (dynvar + atom + schema)"
status: completed
type: task
priority: normal
created_at: 2026-05-08T20:30:48Z
updated_at: 2026-05-08T22:37:39Z
---

## Description

System-wide state today is scattered across defonce atoms in many namespaces (tool.registry/registry, module.loader/activated-modules*, bridge.cancellation/active-turns, config.loader/*config-snapshot*, etc.) plus state-dir threaded through ~50 functions. The Context Pattern unifies these into a single read/register API.

Calling the new namespace `isaac.system` (not `isaac.context`) avoids collision with the existing per-turn `ctx` parameter convention in handlers. ctx stays per-turn; system is the global runtime registry.

This bead introduces the namespace and API only. NO migration of existing atoms — that lands in subsequent beads (a small-atoms migration and a state-dir migration).

## Surface

```clojure
(ns isaac.system)

(def ^:dynamic *system*)

(defn get [k] ...)
(defn register! [k v] ...)
(defn registered? [k] ...)
(defmacro with-system [m & body] ...)
```

The dynvar holds an atom containing a map. Tests bind a fresh atom via `with-system`; production reads from a global default.

## Schema

c3kit/apron schema for the map shape. Known keys (extensible — unknown keys log a warning, do not error, since modules may register their own things):

```
:state-dir          string
:server             any (server instance)
:session-store      any (SessionStore from isaac-o3da)
:config             any (atom or value depending on liveness needs)
:tool-registry      any (atom)
:slash-registry     any (atom)
:comm-registry      any (atom)
:provider-registry  any (atom)
:active-turns       any (atom)
:module-index       any (manifest activation index)
```

The schema reserves these slot names. Modules that register their own concerns use namespaced keys (`:my-module/foo`) per Clojure convention; the schema accepts any namespaced keyword unknown.

## Tasks

1. Create src/isaac/system.clj with API.
2. Define src/isaac/system/schema.clj (or fold into system.clj) with the c3kit schema.
3. Add spec/isaac/system_spec.clj with tests covering: register/get round-trip; with-system isolation; schema validation on register.
4. No production wiring yet — production code does not consume the namespace.
5. README/docstring on system.clj explaining the convention: ctx is per-turn, system is global.

## Out of scope

- Migration of any existing atoms (separate beads).
- State-dir threading migration (separate bead).
- Renaming the per-turn `ctx` parameter (keep ctx for per-turn, system for global — distinct names by design).
- Validation enforcement at startup (future enhancement).

## Why no Gherkin scenarios

Pure infrastructure — no user-visible behavior change. Speclj specs cover the API contract directly. Subsequent migration beads validate against existing feature scenarios as regression tests.

## Acceptance Criteria

bb spec spec/isaac/system_spec.clj passes; bb spec green overall; isaac.system namespace exists with documented API (get, register!, registered?, with-system, *system* dynvar); c3kit schema validates the system map shape and accepts namespaced keywords; no production code reads from or writes to the system yet (migration beads do that).

