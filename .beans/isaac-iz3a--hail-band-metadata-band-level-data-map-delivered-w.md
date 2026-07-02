---
# isaac-iz3a
title: 'Hail band metadata: band-level :data map delivered with every hail, surviving prompt override'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-07-02T14:48:03Z
updated_at: 2026-07-02T16:44:20Z
---

## Context / Motivation

Hail band files currently conflate two channels: **instructions** (the markdown body, which becomes the delivered prompt) and **data** the recipient needs regardless of instructions (bean-repo, notification-comm, plan-hail/work-hail/verify-hail, human-help-comm, ...). Because the data lives in the body prose, a caller that overrides `:prompt` silently discards all of it. Orchestration crews therefore cannot hail each other through bands with a custom explanatory prompt without losing the coordinates (observed on isaac-3692: verifier had no escalation route).

## Design

- New optional `data:` key in band frontmatter (alongside crew / session-tags / reach): an arbitrary map.
- **Delivery semantics:** prompt = caller `:prompt` override, else rendered band body (existing behavior). Band `data` is carried on the hail record and delivery **always** — independent of which prompt was used.
- **Merge:** effective data = `(merge band-data params)` — per-hail `:params` override/extend band defaults.
- **Interpolation:** `{{param}}` placeholders in data values render from params (covers today's `bean: {{bean-id}}`).
- **Model visibility:** surface the effective data through the existing delivery metadata preamble (`delivery_worker.clj` `metadata-preamble`, built by isaac-15y9) — extend that block rather than inventing a new one. Data is also persisted on the hail record (fetchable via the existing hail_get).

## Acceptance criteria (runnable)

- [ ] A band file with `data:` frontmatter parses; invalid/missing `data:` is tolerated (nil map).
- [ ] `isaac hail send --band X --prompt "override"` → delivered prompt is the override AND the band data appears in the `--- Hail metadata ---` preamble.
- [ ] `isaac hail send --band X` (no prompt) → body renders as prompt (existing) and band data appears in the metadata preamble.
- [ ] `--params` keys override same-named band data keys; extra params pass through.
- [ ] `{{param}}` interpolation works inside data values.
- [ ] Data appears on the stored hail record (visible via `--edn`/`--json` send output and the persisted file).
- [ ] Specs/features in isaac-hail cover the above (`bb spec` / `bb features` green).

## Follow-up (not this bean)

- Migrate isaac-* / orchistration-* band files: move data from body prose into `data:` frontmatter; slim bodies to pure instructions; update hail-bean-* skills to reference structured band data.
- Possible shared/default data across a project's bands (dedup) — under discussion.
- Hail threading (:thread-id / :reply-to / hail_get) — separate bean.

## Likely repo scope

isaac-hail (band parsing, send, delivery rendering).



## Verification failed

HEAD: 9c61eaa7c367c9243ba685e3415fc82b160d0bca (isaac-hail)
Working tree: clean

Wrong:
- `bb spec` fails with 3 regressions. `spec/isaac/hail/queue_spec.clj:27` and `spec/isaac/hail/http_spec.clj:28,48` now observe `:data {:n 1}` on hails that only supplied `:params` and targeted a band with no declared band `:data`.
- The broadening comes from `src/isaac/hail/prepare.clj:34-46`: `effective-data` creates `:data` whenever `:params` is non-empty, so plain params are persisted as band metadata even when the band contributed no `data:`.

Missing:
- A regression test for the non-band-data path is missing from the new bean coverage. The existing queue/http specs caught the behavior change, but the new tests do not assert that param-only sends keep `:data` absent.

## Planner resolution (verify fail 316872d3)

The regression is real and the fix is a scoping rule, not a design change:

- **:data exists on a hail record only when the band declares `data:`.** Never synthesize :data from bare :params — a band with no data: contributes nothing, and params-only hails must persist no :data key (this is exactly the 3 spec regressions).
- When the band DOES declare data:, keep the implemented semantics: effective :data = (merge band-data params), params win, {{var}} interpolation in string values.
- Consider adding a config-validate scenario: non-map :data is rejected (accept-case already covered in bands.feature).

Note: the duplicate @wip features/band-data.feature (planner-authored, pre-dating the implementation) has been removed from isaac-hail — features/hail-band-data.feature is the acceptance spec.
