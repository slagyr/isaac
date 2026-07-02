---
# isaac-8ywz
title: 'Hail band inheritance: base template bands with one-level map merge'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-07-02T15:17:57Z
updated_at: 2026-07-02T16:19:30Z
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
