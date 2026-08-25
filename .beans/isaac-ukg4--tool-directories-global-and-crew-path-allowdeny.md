---
# isaac-ukg4
title: 'Tool directories: global and crew path allow/deny'
status: in-progress
type: feature
priority: high
created_at: 2026-08-21T22:20:00Z
updated_at: 2026-08-25T23:20:00Z
blocked_by:
    - isaac-ek0r
    - isaac-da0r
---

Path permission is a yes/no per (crew, path). Closed default: no grants,
no filesystem. Global policy, crew overlay. Hierarchical: longest
matching prefix wins. Same-length prefix uses the same cascade as tools
(crew after global).

Likely repo: **isaac-agent**. Needs **isaac-ek0r** (namespaced `fs/*`)
and **isaac-da0r** (global/crew `:tools` overlay). Does not block MCP.

## Decisions (2026-08-21, Micah)

- **Empty = deny all paths.** `fs/read` of anything fails until a grant
  matches.
- **Two keys, both layers:** `:tools :directories {:allow … :deny …}` on
  root and on crew, beside tool `:allow`/`:deny`.
- **Tokens:** `:cwd` (session workdir and descendants), `:quarters` (that
  crew’s area and descendants), absolute paths (that root and descendants).
  No implicit grants — recommended ship policy is explicit
  `{:allow [:cwd :quarters]}`.
- **Match:** path allowed iff under an allow prefix and no **longer**
  deny prefix applies. Longest prefix wins. Tie → cascade order (global
  allow, global deny, crew deny, crew allow), same as isaac-da0r.
- **Overlay, not replace.** Crew `:deny ["/proj/.env"]` adds a deny.
  Crew omitting `:directories` inherits global.
- Global deny of `~/.ssh` still applies if crew allows `~` (parent is
  weaker). Crew can re-open **that** prefix with `:allow ["~/.ssh"]`.
- **One tree for all `fs/*` tools** (read, write, edit, multi_edit, grep,
  glob). No split read vs write in this bean.
- **exec is not covered.** Directory ACL does not contain a shell.
  Do not assert that `:deny` of a path blocks `exec/run`.
- **Symlinks:** evaluate the resolved path.
- Clean cutover: whatever cwd/quarters heuristic exists today is replaced
  by this config. No back-compat aliases.

## Shape

```edn
;; isaac.edn
:tools {:allow :all
        :deny  [:exec/run]
        :directories {:allow [:cwd :quarters]}}

;; crew/scrapper.edn
:tools {:allow [:exec/run]
        :directories {:allow ["/Users/zane/agents/tonotop"]
                      :deny  ["/Users/zane/agents/tonotop/.env"]}}
```

## Acceptance (`@wip` in isaac-agent)

`features/tool/directories.feature` (file-level `@wip`):

- `bb features features/tool/directories.feature:18`
- `bb features features/tool/directories.feature:36`
- `bb features features/tool/directories.feature:66`
- `bb features features/tool/directories.feature:96`
- `bb features features/tool/directories.feature:127`
- `bb features features/tool/directories.feature:158`
- `bb features features/tool/directories.feature:184`
- `bb features features/tool/directories.feature:215`

New steps invented:

1. `a symlink {path} pointing at {target}`
   Existing file steps write regular files; this scenario needs a link
   whose resolved path is outside the allow root.

## Out of scope

- Sandboxing `exec/run`.
- Separate read vs write trees.
- Path globs other than “directory prefix” (`**`, file patterns).
- MCP.

## Held (awaiting human, 2026-08-25)

Escalated to human by **scrapper**@isaac-work-1. Blocking: isaac-ukg4 directory ACL (longest-prefix + da0r cascade) unit-tested green, but 5/8 approved `directories.feature` scenarios still fail under gherclj (cwd/quarters deny not applying on the live turn path; symlink Given cannot create `/work/project/link.txt` on host). Resumes only on explicit human action (re-hail the work/plan band, or re-promote). No crew re-picks this until then.
