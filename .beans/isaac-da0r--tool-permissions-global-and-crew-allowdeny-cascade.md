---
# isaac-da0r
title: 'Tool permissions: global and crew allow/deny cascade'
status: in-progress
type: feature
priority: high
created_at: 2026-08-21T22:16:00Z
updated_at: 2026-08-23T15:48:23Z
blocked_by:
    - isaac-ek0r
---

Tool permission is a yes/no per (crew, tool). Polar rules in a cascade:
last match wins. Global rules first, then crew. Overlay, not replace —
a crew `:deny` adds a deny; it does not drop global denies.

Likely repo: **isaac-agent**. Needs **isaac-ek0r** (namespaced tokens,
`ns/*`, no unqualified names). MCP (isaac-uhvt) sits on this.

## Decisions (2026-08-21, Micah)

- **Empty config = deny all.** Missing `:allow` is not implicit `:all`.
  Generally-open is an explicit global `:allow :all`.
- **Two keys, both layers:** `:allow` and `:deny` on root `:tools` (beside
  existing `:defaults` / `:web_search`) and on crew `:tools`.
- **Tokens:** namespaced keywords and `ns/*` (ek0r). Policy token `:all`
  (exempt from namespace rule) means every registered tool. `:allow`
  / `:deny` are either the keyword `:all` or a vector of namespaced
  tokens — never `[:all]`. `[:all]` fails validate (`:all` is the list).
- **Cascade (last match wins), in this order:**
  1. global `:allow`
  2. global `:deny`
  3. crew `:deny`
  4. crew `:allow`
- Crew **allow** beats global **deny** (scrapper `:allow [:exec/run]`
  re-enables exec). Crew **deny** beats inherited allows (marvin
  `:deny [:hail/*]`).
- Crew `:deny [:fs/*]` does **not** re-enable exec. Overlay, not replace.
- “Only memory”: crew `:deny :all` then `:allow [:memory/*]` — works
  because crew allow is last. No `:only` key.
- No ship-wide ceiling. Crew file is the trust boundary.
- Clean cutover: omit crew `:tools` **inherits** global (today omit = no
  tools). Crew that must have nothing: `:deny :all`.

## Shape

```edn
;; isaac.edn
:tools {:allow :all
        :deny  [:exec/run]}

;; crew/scrapper.edn
:tools {:allow [:exec/run :linear/*]}

;; crew/marvin.edn
:tools {:deny [:hail/*]}

;; crew/locked.edn
:tools {:deny :all
        :allow [:memory/*]}
```

## Edge

Crew `:deny [:linear/delete_issue]` plus `:allow [:linear/*]` re-allows
delete — crew allow is last. Don’t glob-allow a family you just
punched a hole in; list the tools, or deny after… we have no fifth
step. Accept for v1.

## Acceptance (`@wip` in isaac-agent)

`features/tool/permissions.feature` (file-level `@wip`):

- `bb features features/tool/permissions.feature:11`
- `bb features features/tool/permissions.feature:18`
- `bb features features/tool/permissions.feature:47`
- `bb features features/tool/permissions.feature:79`
- `bb features features/tool/permissions.feature:114`
- `bb features features/tool/permissions.feature:147`
- `bb features features/tool/permissions.feature:171`
- `bb features features/tool/permissions.feature:182`

New steps invented: none.

## Out of scope

- Renaming/mapping builtins (ek0r).
- MCP discovery.
- Ordered free-form rule lists beyond the four-step sugar.
- `:only`.
