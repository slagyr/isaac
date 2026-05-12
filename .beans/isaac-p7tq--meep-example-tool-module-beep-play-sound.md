---
# isaac-p7tq
title: "Meep: example tool module (beep / play sound)"
status: draft
type: feature
priority: deferred
tags:
    - "deferred"
created_at: 2026-05-08T00:29:18Z
updated_at: 2026-05-08T00:29:28Z
---

## Description

A would-be third-party tool-module author has nothing to copy from — only :comm modules exist (Discord, Telly). Meep fills that gap: a small, harmless tool module whose only purpose is to demonstrate the pattern.

## What Meep does

Single tool, exposed to the LLM as `meep`. Makes the host machine emit a sound. Two modes:

- `meep` (no args, or `{"kind":"bell"}`)  — emit ASCII BEL () to stderr. Works everywhere; degrades to silent on terminals without bell.
- `meep {"kind":"sound","name":"glass"}`  — play a named system sound. macOS: `afplay /System/Library/Sounds/<name>.aiff`. Other OSes: log a "not supported" warning, fall back to BEL.

Returns `{:result "beeped"}` or similar small confirmation. The point is to exercise the registration pipeline, not to be a useful sound library.

## Module structure (the actual reference for third-party authors)

```
modules/isaac.tool.meep/
  resources/isaac-manifest.edn
  src/isaac/tool/meep.clj
  deps.edn
```

`isaac-manifest.edn`:
```
{:id      :isaac.tool.meep
 :version "0.1.0"
 :entry   isaac.tool.meep
 :extends {:tool {:meep {}}}}
```

`isaac/tool/meep.clj`:
```clojure
(ns isaac.tool.meep
  (:require [isaac.tool.registry :as tool-registry]
            [isaac.util.shell :as shell]))

(defn- meep-tool [args] ...)

(def meep-spec
  {:name        "meep"
   :description "Emit a beep or play a named system sound on the host machine."
   :parameters  {:type "object"
                 :properties {"kind" {:type "string" :description "bell or sound; default bell"}
                              "name" {:type "string" :description "system sound name (e.g. glass, ping, sosumi); only used when kind=sound"}}
                 :required []}
   :available?  (fn [] true)
   :handler     #'meep-tool})

(defn -isaac-init []
  (tool-registry/register! meep-spec))
```

## Why deferred

- The tool itself is novelty; Isaac's core needs aren't blocked by this.
- Best filed AFTER isaac-tw37 (:available? predicate) lands so the module can demonstrate that field too.
- Best filed AFTER the manifest's :extends value-spec is tightened (one of the gaps for tool-extension) so Meep validates against the real schema.

When picked up, Meep becomes the canonical example referenced by manifest-extension docs and by isaac-mx1d (third-party module support).

## Acceptance

- modules/isaac.tool.meep/ exists with manifest, src, deps.
- Starting Isaac with the module declared in :modules registers `meep` and the LLM can invoke it.
- BEL mode works everywhere; sound mode works on macOS, no-ops elsewhere with a logged warning.
- Documentation (or this bead's notes) calls out Meep as the reference for third-party tool modules.

