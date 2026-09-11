---
# isaac-8ywz
title: 'Hail band inheritance: base template bands with one-level map merge'
status: completed
type: task
priority: normal
created_at: 2026-07-02T15:17:57Z
updated_at: 2026-07-02T17:16:52Z
blocked_by:
    - isaac-iz3a
---

## Context / Motivation

With band-level `data:` (isaac-iz3a), coordinate data (bean-repo, notification-comm, human-help-comm, ...) would be duplicated across all of a project's bands (isaac-plan / isaac-work / isaac-verify). Duplication drifts silently — stale coordinates fail quietly (wrong channel, missing escalation band). Let bands compose over a shared template band.

## Design

- New optional frontmatter key **`base: <band-name>`** — references another band file by name (file stem, no `.md`), e.g. `base: _isaac-template`.
- **Merge semantics** (child over base) — one-level map merge, child wins on scalars:

```clojure
(merge-with (fn [a b] (if (and (map? a) (map? b)) (merge a b) b)) base child)
```

  i.e. map-valued keys (like `data:`) merge key-wise with child winning per key; scalar/vector values (crew, reach, session-tags) are replaced wholesale by the child. Do NOT implement literal `(merge-with merge ...)` — it throws on scalar conflicts.
- **Body:** child body wins; a blank/absent child body falls back to the base's body.
- **Recursion:** a base may itself declare `base:`; resolve transitively with **cycle detection** (error clearly on a cycle, do not hang).
- **Template naming convention:** band files starting with `_` are templates — they participate in inheritance but are **not addressable** (excluded from band matching / `--band` resolution; hailing one is an error). No new frontmatter flag.

## Example

`config/hail/_isaac-template.md`:
```yaml
---
session-tags: [:isaac]
reach: :one
data:
  bean-repo: git@github.com:slagyr/isaac.git
  notification-comm: {:id :discord :channel "isaac"}
  human-help-comm: {:id :imessage :target "micahmartin@mac.com" :service "iMessage"}
---
```

`config/hail/isaac-verify.md`:
```yaml
---
base: _isaac-template
crew: perceptor
data:
  plan-hail: isaac-plan
  work-hail: isaac-work
---
<verify instructions>
```

## Acceptance scenarios

Gherkin written as @wip in isaac-hail `features/band-inheritance.feature` (inherited tags/data, per-key child-wins data merge, body fallback, transitive chains, cycle error, missing-base error, `_` templates not addressable). Un-@wip as implemented.

## Acceptance criteria (runnable)

- [ ] Band with `base:` resolves: base frontmatter merged under child per the semantics above.
- [ ] Map keys (`data:`) merge key-wise; child key wins on conflict; base-only keys survive.
- [ ] Scalar keys (crew, reach) in child replace base wholesale; no merge error on scalar conflict.
- [ ] Child body wins; blank child body inherits base body.
- [ ] Transitive base chains resolve; a cycle produces a clear error.
- [ ] `_`-prefixed band files are not addressable: excluded from band matching; `hail send --band _x` errors.
- [ ] Missing base reference produces a clear error at load/send time.
- [ ] Specs/features in isaac-hail cover the above (`bb spec` / `bb features` green).

## Follow-up (not this bean)

- Migrate isaac-* / orchistration-* bands onto `_<project>-template.md` bases.

## Likely repo scope

isaac-hail (band loading/resolution).



## Verification failed

HEAD: 39e94aad3ddb6083265710991dc2462cedf092b9 (isaac-hail)
Working tree: clean

Wrong:
- `bb spec` is green, but `bb features` fails and the failure reproduces in isolation with `bb features features/band-inheritance.feature` (6 failures, 1 pass).
- The acceptance gaps are the core inheritance behaviors in `features/band-inheritance.feature`: inherited base data is missing, blank-child body fallback is missing, transitive base-chain data is missing, and `config validate` does not surface the expected cycle / missing-base errors.
- The isolated reproducer shows concrete misses: the child/base merge scenario persists only `{:notification-channel "engine"}` instead of also keeping base-only `:bean-repo`; the body-fallback scenario gets `nil` prompt instead of the base template body.

Risky:
- The unit/spec surface around `src/isaac/hail/band_resolve.clj` is not exercising the same end-to-end path as `hail send` / `config validate`; the implementation claims resolution at load time, but the acceptance path still behaves as if base resolution is not reaching band send/validation reliably.
