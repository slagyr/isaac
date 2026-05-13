---
# isaac-15hw
title: 'Ref-based config validation: existence predicates for every config ref'
status: completed
type: feature
priority: low
created_at: 2026-05-11T23:21:33Z
updated_at: 2026-05-13T20:06:23Z
---

## Description

Today the config schema only type-checks fields that reference other
configured entities. A typo'd model name (:crew.main.model "claud"
when the model is :claude), an unknown provider, a non-existent crew
in cron — all pass schema and only surface at dispatch time (or never,
if that path isn't exercised).

This bead introduces a generic :validations annotation on config
schema fields, plus a set of named existence predicates that cover
every cross-reference in Isaac's config.

## Validation annotation shape

  :crew {:value-spec
         {:schema {:model    {:validations [:model-exists?]}
                   :provider {:validations [:provider-exists?]}
                   :tools {:type :map :schema
                           {:allow {:type :seq
                                    :spec {:validations [:tool-exists?]}}}}}}}

A field with :validations runs each named predicate against the
field's value during config-load validation. Predicates are boolean
(? suffix per Clojure idiom). Failure produces a structured error
naming the file, the path, the bad value, and (where applicable) the
valid alternatives.

## Existence predicates this bead ships

  :llm-api-exists?    value is in the merged manifest's :llm/api keys
  :tool-exists?       value is in the merged manifest's :tool keys
  :provider-exists?   value is in manifest :provider ∪ config :providers
  :comm-exists?       value is in manifest :comm ∪ config :comms
  :model-exists?      value is in config :models
  :crew-exists?       value is in config :crew

Each predicate has a small resolver function that knows where to look
for its target set in the merged manifest + merged config. All run
without loading any impl namespace — depends on isaac-yonq making
manifest-derived sets available pre-namespace-load.

## Wired-up field annotations

  :defaults.crew                            → [:crew-exists?]
  :defaults.model                           → [:model-exists?]
  :providers.<id>.api                       → [:llm-api-exists?]
  :models.<id>.provider                     → [:provider-exists?]
  :crew.<id>.model                          → [:model-exists?]
  :crew.<id>.provider                       → [:provider-exists?]
  :crew.<id>.tools.allow[*]                 → [:tool-exists?]
  :cron.<job>.crew                          → [:crew-exists?]
  :hooks.<id>.crew                          → [:crew-exists?]
  :hooks.<id>.model                         → [:model-exists?]
  :comms.<id>.crew                          → [:crew-exists?]

## Naming convention

All validation predicate names end in ?, matching Clojure's predicate
idiom (e.g. (nil? x), (some? x)). Future non-ref validations follow
the same convention: :effort-in-range?, :port-in-range?, :non-empty?,
:absolute-path?, :valid-cron-expr?, :valid-url? etc.

## Depends on

- isaac-yonq (manifest promotion). Required because :llm-api-exists?,
  :tool-exists?, :provider-exists?, and :comm-exists? all need the
  manifest's :extends keys readable without loading impl namespaces.

## What this catches

Real user incident drove this: zanebot's openai-codex provider had
:api "openai-compatible" (deprecated, then dropped). The user saw
{:detail "Not Found"} from upstream OpenAI instead of a clear config
error. With :llm-api-exists? on :providers.value.api, the same
mistake would have failed at config load with: ":api 'openai-compatible'
is not a registered API. Valid: openai-completions, openai-responses,
anthropic-messages, ollama, claude-sdk, grover."

Similar payoff for the other refs: typoed model in a crew config,
misnamed provider, cron pointing at a deleted crew.

## Acceptance Criteria

- New :validations key supported by the config schema layer; each
  entry runs as a named predicate against the field value.
- All six existence predicates implemented and named with ? suffix:
  :llm-api-exists?, :tool-exists?, :provider-exists?, :comm-exists?,
  :model-exists?, :crew-exists?.
- src/isaac/config/schema.clj annotates every cross-reference field
  per the wired-up list in the description.
- Predicates resolve target sets from merged manifest + merged config
  without loading impl namespaces.
- Validation failures produce structured error: file path, field
  path, bad value, and where applicable, the valid set.
- features/cli/config.feature gains a scenario per existence
  predicate, asserting the error shape on a known-bad value.
- bb features green; bb spec green.

## Notes

- Original 15hw scope was just :llm-api-exists? on :providers.value.api.
  Broadened during planning to cover every ref in the config tree —
  same plumbing, just more wired-up annotations.
- The :validations vector accommodates non-ref checks (range, format,
  etc.) under the same convention; future beads can add those without
  changing the framework.
Implemented named ref validations across the config schema, added existence predicates for llm api/tool/provider/comm/model/crew refs, and expanded CLI config validation scenarios to assert file, bad value, and valid-set details.

Checks
- bb spec (green)
- bb features (green except unrelated features/context/compaction.feature scenario tracked in isaac-2mv2)
Addressed reopened review findings by wiring schema-level :validations for config refs, implementing named existence predicates, and moving ref validation to a manifest+config-only predicate framework with richer error entries.

Checks
- bb spec (green)
- bb features (green except unrelated features/context/compaction.feature scenario tracked in isaac-2mv2)
