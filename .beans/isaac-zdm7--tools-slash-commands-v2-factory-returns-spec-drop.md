---
# isaac-zdm7
title: 'Tools + slash-commands v2: factory-returns-spec, drop :sort-index, retire register-schema!'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-14T20:26:34Z
updated_at: 2026-05-14T21:25:00Z
blocked_by:
    - isaac-zl32
---

Companion to [[isaac-zl32]] (manifest v2): revise `:tools` and `:slash-commands` manifest entries to factory-returns-spec shape, move LLM-facing metadata into code, retire `register-schema!`, drop `:sort-index` everywhere.

## Scope

- Pluralize manifest keys: `:tool` → `:tools`, `:slash-command` → `:slash-commands`.
- New per-entry shape for both kinds: `{:factory <sym> :schema {...}}` — flat, no `:description` or `:parameters` or `:sort-index` at manifest level.
- Move `:description`, `:handler`, `:parameters` (tools) into the factory's return value. Same place changes together with the code.
- Factory signature: `(factory user-config) → spec-map`. For tools that need no config, factory ignores its arg and returns a fixed spec.
- `register-schema!` retires. Per-tool / per-slash-command schemas declared in the manifest entry.
- `:sort-index` removed from manifest and from registry data; ACP-side dropdown order for built-ins hard-coded at the serialization layer.
- Manifest installs every tool by default; user config parameterizes the subset that needs it.

## User-config layout

- **Root `:tools {:web_search {:api-key "..." :provider :brave}}`** — installation params per tool. Validated against the tool entry's `:schema`. Required-but-missing field → rejected at config load.
- **Crew `:tools {:allow [...] :directories [...]}`** — per-crew gating, unchanged shape from today. (Same `:tools` key at two scopes; positional disambiguation.)
- **Root `:slash-commands {:echo {:command-name "..."}}`** — per-command config, unchanged location. Validated against the command entry's `:schema`.

## Per-kind entry shape

| Kind | Manifest keys | Factory return |
|---|---|---|
| `:tools` | `:factory`, `:schema` | `{:description :parameters :handler}` |
| `:slash-commands` | `:factory`, `:schema` | `{:description :command-name :handler}` |

## ACP autocomplete ordering

Hard-coded priority list for built-in slash commands (`/status`, `/model`, `/crew`, `/cwd`, `/effort`) in the ACP serialization layer, followed by alphabetical for the rest. Module slash commands always sort alphabetically. No more `:sort-index` field on entries.

## New `@wip` scenario

- `features/modules/tool_extension.feature:12` — Tool config rejected when a required `:schema` field is missing. Pins declarative manifest-level schema validation for tools.

## Existing scenarios — mechanical migration

`features/modules/slash_extension.feature`:

- All scenarios: rename `:isaac/factory` to `:factory` in any inline manifests.
- Line 46 ("A configured slash command overrides a built-in"): the `:command-name "status"` override pattern keeps working but now via factory-returned `:command-name` rather than registry-side override. Behavior unchanged; assertion stays.

Web search:

- `isaac.tool.web-search/register-schema!` call removed; schema migrates into the manifest entry.
- Any scenarios that exercise web search's config knobs continue to pass.

## Unit specs

In `spec/isaac/module/manifest_spec.clj` (extending zl32's coverage):

- Tool manifest entry missing `:factory` → schema rejects.
- Slash-command manifest entry missing `:factory` → schema rejects.
- Tool manifest entry with `:isaac/factory` (v1 namespace) → schema rejects.
- Tool entry with `:sort-index` → schema rejects (key removed).
- Slash-command entry with `:sort-index` → schema rejects.

In `spec/isaac/tool/registry_spec.clj` (or equivalent):

- Factory called with user-config produces a spec; spec is registered correctly.
- Factory ignores user-config it doesn't declare (open-set schema).

In `spec/isaac/slash/registry_spec.clj`:

- Factory called with user-config; resulting spec's `:command-name` becomes the registered name.
- `all-commands` returns alphabetical order (no `:sort-index` consulted).

In `spec/isaac/comm/acp_spec.clj`:

- `available-commands-update` serializes built-ins in hard-coded priority order followed by alphabetical others.

## Acceptance

- [ ] `manifest-schema` extended (or kept consistent with zl32) — `:tools` and `:slash-commands` per-entry shape locked.
- [ ] Built-in tools in `src/isaac-manifest.edn` migrated to new shape (eleven tools).
- [ ] Built-in slash-commands in `src/isaac-manifest.edn` migrated (status, model, crew, cwd, effort).
- [ ] Built-in tool factories rewritten to return `{:description :parameters :handler}` maps.
- [ ] Built-in slash-command factories rewritten to return `{:description :command-name :handler}` maps.
- [ ] `isaac.tool.web-search` schema migrated from `register-schema!` to manifest `:schema`.
- [ ] `isaac.slash.echo` (fixture module) manifest migrated; its `:command-name` field spec moves under `:schema`.
- [ ] `register-schema!` function deleted (no callers remain).
- [ ] `slash-registry/all-commands` simplified to alphabetical sort (no `:sort-index` reference).
- [ ] ACP comm hard-codes built-in priority ordering for `available-commands` serialization.
- [ ] `@wip` removed from `features/modules/tool_extension.feature:12`; scenario passes.
- [ ] Existing `slash_extension.feature` scenarios pass after rename.
- [ ] Unit specs added per "Unit specs" section.
- [ ] Run: `bb features features/modules/tool_extension.feature features/modules/slash_extension.feature` + full spec suite.

## Out of scope (separate beans)

- `:web-search-provider-exists?` and other named-validator registration mechanism — a generalized hook for declarative cross-config integrity checks. Surfaced during this discussion; deferred until needed.
- Multi-instance tools via `:type` dispatch — YAGNI'd; manifest is forward-compatible if added later.
- Per-user dropdown ordering knob (`:slash-command-order [...]` or similar) in user config — YAGNI'd.

## Dependencies

Blocked by [[isaac-zl32]] (manifest v2). That bean establishes the universal namespace drop, `:schema` slot, `:factory` key convention, and pluralization pattern that this bean extends to tools and slash-commands.
