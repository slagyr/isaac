---
# isaac-7in9
title: Extract shared {{var}} template renderer (de-dupe render-template)
status: completed
type: task
priority: normal
created_at: 2026-06-24T00:09:28Z
updated_at: 2026-06-24T00:23:57Z
---

Two independent copies of a `{{var}}` template renderer exist; consolidate into one shared helper in isaac-agent so future hail templating (band-prompt/payload) can reuse it instead of adding a third copy.

## Duplicated sites
- `isaac-agent/src/isaac/prompt/catalog.clj:160` render-template — reduce-kv over KNOWN bindings (string params), missing var -> "" (empty). Renders command/skill bodies with arg bindings.
- `isaac-hooks/src/isaac/hooks.clj:132` render-template — regex-scans template `#"\{\{(\w+)\}\}"`, keyword key lookup, missing var -> "(missing)". Renders a hook :template with the inbound webhook body.

## Behavioral differences to reconcile (do NOT silently change behavior)
1. Missing-variable policy: catalog -> empty string; hooks -> "(missing)". Shared fn must PARAMETERIZE this (e.g. :on-missing :empty | :marker | :keep) and each caller passes the value that preserves today's behavior.
2. Key type: catalog string param keys; hooks keyword keys. Coerce at the boundary so both keep working.
3. Strategy: catalog reduces over the binding set (unknown {{...}} left as-is); hooks regex-scans (every {{word}} resolved, missing -> marker). Make regex-scan canonical; route catalog's behavior via :on-missing.

## Proposed
- New ns in **isaac-agent** under the existing prompt namespace, e.g. `isaac.prompt.template`, `(render template vars & {:keys [on-missing]})`. NOT foundation — foundation has no internal consumer for a `{{var}}` renderer, and both current consumers already live at/above the agent layer (catalog is in agent; isaac-hooks already depends on isaac-agent and requires agent nses). Putting it in foundation would push a util down into a layer that never uses it just to share it upward.
- Replace both private render-templates with calls; each passes the :on-missing preserving current behavior.
- Unit-spec the shared fn: known var, missing under each policy, keyword vs string keys, unknown placeholder, nil template.

## Acceptance
- Single {{var}} renderer in isaac-agent (`isaac.prompt.template`); catalog.clj + hooks.clj call it; no local copies remain.
- catalog still renders missing -> ""; hooks still missing -> "(missing)" (preserved, spec-covered).
- agent spec covers the renderer directly (`spec/isaac/prompt/template_spec.clj`).

## Notes
Surfaced 2026-06-23 investigating hail :payload intent — likely a band :prompt TEMPLATE filled by the hail :payload (the hooks :template + webhook body pattern), never wired. Any future hail-templating work should consume THIS shared renderer, not add a third copy. Mustache-lite (`{{word}}`) only — not a full engine.

## Worker handoff (2026-06-24, superseded)

Initial implementation placed renderer in foundation (`3b3a6b1`); reversed after home decision below.

## Worker handoff (2026-06-24, current)

- `isaac-foundation` `1685b76`: removed mistaken `isaac.template` (foundation has no consumer).
- `isaac-agent` `ad1a3b6`: `isaac.prompt.template/render` with `:on-missing` `:keep` | `:empty` | `:marker`; `spec/isaac/prompt/template_spec.clj` (9 examples); `catalog.clj` uses `{:on-missing :keep}`.
- `isaac-hooks` `d905452`: `hooks.clj` requires `isaac.prompt.template`, pins agent `ad1a3b6`; `{:on-missing :marker}`; hooks specs green.

## Home decision (2026-06-23, Micah): isaac-agent, not foundation
Verified: isaac-hooks deps.edn depends on isaac-agent and hooks.clj already requires isaac.bridge.core / isaac.session.* (agent nses). So the two consumers (agent catalog.clj + hooks.clj) both sit at/above agent. Helper lives in isaac-agent `isaac.prompt.template`. Foundation rejected: it has no internal use for a {{var}} renderer (its own ${var} env-interpolation in config/loader is consumed by foundation itself; this is not). Future hail-templating consumer reaches it via its existing agent dependency (hail dispatches turns through the bridge/charge path — confirm at impl time).
