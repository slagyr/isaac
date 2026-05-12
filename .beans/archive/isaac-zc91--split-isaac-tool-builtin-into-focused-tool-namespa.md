---
# isaac-zc91
title: "Split isaac.tool.builtin into focused tool namespaces"
status: completed
type: task
priority: low
created_at: 2026-05-08T00:26:08Z
updated_at: 2026-05-08T15:49:06Z
---

## Description

isaac.tool.builtin is 853 lines holding 10+ tool implementations plus their registration scaffolding. Three sibling files exist as misleading placeholders — isaac.tool.glob (3 lines), isaac.tool.web-fetch (3 lines), isaac.tool.web-search (1 line) — each declaring a namespace and (in two cases) a single dynamic var, but holding none of the actual tool implementations.

Someone reading src/isaac/tool/ sees 6 files and assumes 6 tool implementations per file; really it is 1 file with 12 tools and 3 hollow placeholders. This looks like an aborted "extract each tool to its own file" refactor.

## Proposed split

Move tool implementations out of builtin.clj into focused namespaces, completing the placeholders:

- isaac.tool.file       — read-tool, write-tool, edit-tool
- isaac.tool.glob       — glob-tool (already has the placeholder + *default-head-limit* dynamic var)
- isaac.tool.grep       — grep-tool (currently in builtin) + the rg-availability check
- isaac.tool.web-fetch  — web-fetch-tool (placeholder ready)
- isaac.tool.web-search — web-search-tool (placeholder ready)
- isaac.tool.exec       — exec-tool, start-process / destroy-process / wait-for-process helpers
- isaac.tool.session    — session-info-tool, session-model-tool

After the split, isaac.tool.builtin holds only:
- ordered-built-in-tools (the registration order)
- built-in-tool-specs (name → spec map, referencing handlers from the new namespaces)
- register-all! / register-built-in-tool! (the registration entry point)

It becomes a thin manifest-and-registration namespace, ~100 lines.

## Acceptance / migration considerations

- Specs in spec/isaac/tool/builtin_spec.clj split alongside (per-tool spec files match per-tool source files).
- The :handler refs in built-in-tool-specs change from #'tool-name (in-ns) to #'isaac.tool.file/read-tool etc.
- The shared filesystem-boundary helpers at the top of builtin.clj (canonical-path, path-inside?, state-dir->home, config-directories, crew-quarters, string-key-map, arg-bool, arg-int) move to a new isaac.tool.fs-bounds (or stay in builtin if usage is constrained to one or two new ns).
- Verify no third-party module's manifest assumed handler refs in isaac.tool.builtin (probably none today, but worth the grep).

## Out of scope

- Memory tools (already in isaac.tool.memory; leave alone).
- The :available? predicate refactor (separate bead).
- The registry-ns indirection audit (separate bead).

## Acceptance Criteria

bb spec green; bb features green; src/isaac/tool/builtin.clj is under 200 lines; isaac.tool.{file,grep,web-fetch,web-search,exec,session} all hold the corresponding tool implementation; the placeholder isaac.tool.glob has the actual glob-tool implementation alongside its dynamic var; no production caller of moved tool fns is left broken; existing built-in-tool-specs keys and behavior unchanged from the LLM's perspective.

