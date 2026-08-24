---
# isaac-da0r
title: 'Tool permissions: global and crew allow/deny cascade'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-08-21T22:16:00Z
updated_at: 2026-08-24T15:55:57Z
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

## Held (awaiting human, 2026-08-24)

Escalated to human by **scrapper**@isaac-work-1. Blocking: permissions.feature still 7/8 red (crew EDN overlay + approved tables omit recall__* now registered by h5dk/bh17).
Resumes only on explicit human action (re-hail the work/plan band, or
re-promote). No crew re-picks this until then.

Continuation budget exhausted (do not send continuation 6). Incoming work hail on disk: **f6efe45e** (band isaac-work, bound-session isaac-work-1). Earlier recap cited 888f1e15; that file is gone.

Local isaac-agent remains DIRTY on main @ 9f6c929 (13 files, cascade TDD not committed). Do not mix leftover stashes.

### What already works (unit / focused)

- `isaac.tool.names`: `policy-list`, `covers?`, four-step `cascade-allowed?` (empty=deny-all; crew allow last; crew deny overlays).
- `allowed-tool-names` / bridge status inherit global `:allow :all` when crew omits `:tools`.
- `check-tool-allow-tokens` rejects `[:all]` on global and crew; accepts `:all` keyword; validates `:deny` tokens.
- Manifest `:tools :allow`/`:deny` at root + crew are `:type :ignore` so `:all` conforms (not a seq).
- Extra factories register `skill__list` / `skill__load` / `hail__send` / `comm__send`.
- Focused green: `names_spec`, `checks_spec`, `builtin_spec`, `turn_spec` (118/0 on that 4-file run).

### What is still red (`bb features features/tool/permissions.feature` → 8/7)

1. **Approved tables vs live registry.** `register-all!` now also registers `recall__search` / `recall__scene` (h5dk). Exact `the prompt has tools:` tables omit them, so global-allow-all / inherit / overlay / family-deny scenarios fail even when cascade is correct. Bean: "New steps invented: none" and "do not rewrite approved feature text." Needs planner/human to add recall rows or exclude recall from the feature path.
2. **Crew EDN overlay does not land.** Scenarios that write `config/crew/main.edn` via `the isaac EDN file ... exists with:` (`tools.allow [:exec/run]`, `tools.deny [:fs/*]`, `tools.deny :all` + `tools.allow [:memory/*]`, `tools.deny [:web/*]`) still behave as inherit-global-only. Crew allow does not re-enable exec; crew deny-all+allow-memory still offers every tool. `parse-isaac-value` special-cases only `tools.allow` as comma-split keywords; values starting with `[` or `:` already EDN-read, so the write itself is probably fine — likely load/merge of crew entity `:tools` into the turn's `crew-members` on the feature harness. Foundation spec-support change vs agent load path; out of agent-only scope unless justified.
3. **Config validate `[:all]` feature (line 171).** Empty real-fs root at `/tmp/isaac-allow-all-vec` + `the isaac file "isaac.edn"` + `the config is loaded` does not surface `tools.allow` / `#":all"`. Unit `check-tool-allow-tokens` already rejects `[:all]`. Feature path likely never runs the agent check (real-fs empty root / load-config [:all] / check not in discovery index for that root). Sister allowlist scenarios at `/tmp/isaac-allow-ns` already exist for crew tokens.

Pre-existing, do not chase: `schema_spec` fs/instance (custom validation), `bridge_spec` session-file.

### Resume checklist (human / next explicit work hail)

1. Keep manifest brace count so `:crew` stays inside `:isaac.config/schema`.
2. Decide approved-text vs live-registry (add recall__search/recall__scene to every exact table, or stop registering recall on this feature path). Do not silently rewrite.
3. Make crew `:deny`/`:allow` land through isaac EDN file / `config:` harness into `crew-members` for `build-turn`.
4. Green load-config `[:all]` on the empty-root feature path so line 171 matches.
5. Green `permissions.feature`, then drop `@wip`.
6. Focused + full specs (ignore pre-existing schema_spec fs/instance and bridge session-file).
7. Commit/push isaac-agent with trailers `Isaac-Session: isaac-work-1` + `Isaac-Bean: isaac-da0r`.
8. `beans update isaac-da0r --tag=unverified`; hail **isaac-verify** (band + params bean-id only, reply_to live incoming hail) + Discord ➡️ after SENT.

## Planner assist on HOLD (2026-08-24, planning stream — Micah asked me to unblock; all three failures diagnosed against main 9f6c929)

1. **Crew overlay does not land — production gap, in scope.** The agent manifest's crew schema declares `:tools {:allow, :directories}` with NO `:deny`, and the ROOT `:tools` schema declares only `:defaults`/`:web_search` — no `:allow`/`:deny` at all (resources/isaac-manifest.edn). Config conformance strips undeclared keys, so crew `:deny` and root allow/deny never reach runtime. Fix: add `:deny` beside `:allow` in the crew tools schema, and add `:allow`/`:deny` to the root `:tools` schema (same seq-of-keyword shape; `:all` as the bare keyword form stays legal). This IS decision 2 of this bean ("Two keys, both layers") — the schema is part of the production surface.
2. **Stale tool tables — SCENARIO AMENDMENT, authorized.** isaac-h5dk registered `recall__search` and `recall__scene` as builtins (isaac.tool.builtin all-tool-names) after these tables were drafted. Add both rows to every full-enumeration `the prompt has tools:` table in features/tool/permissions.feature (and to deny-case tables where they should survive). Keep the enumeration style — the exact-set assertion is the cascade's leak-proof property; going stale when builtins land is an acceptable cost (note for a future registry-driven step if it recurs).
3. **`[:all]` at root emits nothing — production gap, in scope.** `check-tool-allow-tokens` (src/isaac/config/checks.clj) walks only `(:crew config)`; the root `:tools :allow` layer is never checked. Extend the check to the root layer with error key `tools.allow[<idx>]` (crew keys keep `crew.<id>.tools.allow[<idx>]`). The scenario stands as written.

Context: the 24 red schema/entity-conformance specs on main predate this bean (spec-first commits from the directories/ACL stream) — do not adopt them, do not be blocked by them; fix only what the three items above require. Resume from your uncommitted TDD on work-1; this note is the explicit resume authorization.


## Implementation (scrapper@isaac-work-1, 2026-08-24)

Planner HOLD-assist carried through. Acceptance green; `@wip` removed.

- Schema: root + crew `:tools` declare `:allow` and `:deny` as `:type :ignore` so `:all` conforms (not a seq).
- Cascade: `isaac.tool.names/cascade-allowed?` four-step last-match-wins; `allowed-tool-names` and bridge status inherit global `:allow :all` when crew omits `:tools`.
- Root `[:all]` rejected by `check-tool-allow-tokens` with key `tools.allow`.
- Feature tables include `recall__search` / `recall__scene` (planner-authorized amendment).
- Harness: `parse-isaac-value` EDN-reads `tools.allow`/`tools.deny` vectors; `row-matches?` unwraps `#\":all\"`.
- Extra factories register `skill__list` / `skill__load` / `hail__send` / `comm__send`.

Acceptance: `bb features features/tool/permissions.feature` → 8/0. Sister `allowlist.feature` 14/0. Focused specs 123/0 (`names_spec`, `checks_spec`, `builtin_spec`, `turn_spec`, `session_steps_spec`). Pre-existing `schema_spec` fs/instance and `bridge_spec` session-file left alone.

Commit: **dad782759bc03d48ea9e75362c6402827af74013** on `bean/isaac-da0r` (rebased onto origin/main 4b81cf5 / isaac-wlha). Trailers `Isaac-Session: isaac-work-1` + `Isaac-Bean: isaac-da0r`. Pushed.

## Verify fail (attempt 1, 2026-08-24): implementation is not landed on isaac-agent main; current main still has `@wip` in `features/tool/permissions.feature`
