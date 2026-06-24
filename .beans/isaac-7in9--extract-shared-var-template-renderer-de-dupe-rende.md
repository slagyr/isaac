---
# isaac-7in9
title: Extract shared {{var}} template renderer (de-dupe render-template)
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-24T00:09:28Z
updated_at: 2026-06-24T00:20:00Z
---

Two independent copies of a `{{var}}` template renderer exist; consolidate into one shared helper in foundation so future hail templating (band-prompt/payload) can reuse it instead of adding a third copy.

## Duplicated sites
- `isaac-agent/src/isaac/prompt/catalog.clj:160` render-template — reduce-kv over KNOWN bindings (string params), missing var -> "" (empty). Renders command/skill bodies with arg bindings.
- `isaac-hooks/src/isaac/hooks.clj:132` render-template — regex-scans template `#"\{\{(\w+)\}\}"`, keyword key lookup, missing var -> "(missing)". Renders a hook :template with the inbound webhook body.

## Behavioral differences to reconcile (do NOT silently change behavior)
1. Missing-variable policy: catalog -> empty string; hooks -> "(missing)". Shared fn must PARAMETERIZE this (e.g. :on-missing :empty | :marker | :keep) and each caller passes the value that preserves today's behavior.
2. Key type: catalog string param keys; hooks keyword keys. Coerce at the boundary so both keep working.
3. Strategy: catalog reduces over the binding set (unknown {{...}} left as-is); hooks regex-scans (every {{word}} resolved, missing -> marker). Make regex-scan canonical; route catalog's behavior via :on-missing.

## Proposed
- New foundation ns, e.g. `isaac.template`, `(render template vars & {:keys [on-missing]})`. Foundation is the shared seed both isaac-agent and isaac-hooks already depend on.
- Replace both private render-templates with calls; each passes the :on-missing preserving current behavior.
- Unit-spec the shared fn: known var, missing under each policy, keyword vs string keys, unknown placeholder, nil template.

## Acceptance
- Single {{var}} renderer in foundation; catalog.clj + hooks.clj call it; no local copies remain.
- catalog still renders missing -> ""; hooks still missing -> "(missing)" (preserved, spec-covered).
- foundation spec covers the renderer directly.

## Notes
Surfaced 2026-06-23 investigating hail :payload intent — likely a band :prompt TEMPLATE filled by the hail :payload (the hooks :template + webhook body pattern), never wired. Any future hail-templating work should consume THIS shared renderer, not add a third copy. Mustache-lite (`{{word}}`) only — not a full engine.

## Worker handoff (2026-06-24)

- `isaac-foundation` `3b3a6b1`: new `isaac.template/render` with `:on-missing` `:keep` | `:empty` | `:marker`; `spec/isaac/template_spec.clj` (9 examples).
- `isaac-agent` `9d13ad5`: `catalog.clj` calls `template/render` with `{:on-missing :keep}`; catalog specs green.
- `isaac-hooks` `e5cee84`: `hooks.clj` calls `tpl/render` with `{:on-missing :marker}`; hooks specs green (policy asserted via `isaac.template/render`).
