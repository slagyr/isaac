---
# isaac-ek0r
title: 'Tool allowlist: namespaced keywords and namespace globs'
status: draft
type: feature
priority: high
created_at: 2026-08-21T22:15:25Z
updated_at: 2026-08-21T22:16:00Z
---

Every tool has a namespace. Config uses Clojure namespaced keywords;
the model sees `ns__name`. Un-namespaced tool names and allow/deny
tokens are a validation error. `ns/*` globs a family.

Likely repo: **isaac-agent**. Blocks **isaac-da0r** (cascade) and
**isaac-uhvt** (MCP).

## Decisions (2026-08-21, Micah)

- **No un-namespaced tools.** Registering or allowing `read` is invalid.
  `:fs/read` is the config token; `fs__read` is the wire/registry/LLM name
  (`^[a-zA-Z0-9_-]{1,64}$` — `/` is illegal on the wire).
- **Clean cutover.** Builtins are renamed; old wire names are gone (no
  aliases). Existing crew `:allow` lists and features must move with them.
- Glob is namespace-only: `:fs/*` matches prefix `fs__`. Bare `*` is not
  a glob (policy token `:all` lives on isaac-da0r).
- Policy token `:all` is **not** a tool name; it is exempt from the
  namespace rule. Everything else in `:allow`/`:deny` must be namespaced
  (`:linear/get_issue`, `:fs/*`).
- Namespaced allow/deny entries need not name a live tool at validate
  time (MCP may be down). They must have a namespace, and `*` is only
  legal as the name of a namespaced keyword.
- Default-deny and global/crew overlay are **isaac-da0r**, not this bean.
  This bean is identity + matching: canonicalize tokens, reject
  unqualified, glob `ns/*`, rename builtins.

## Builtin mapping (config → wire)

| Config | Wire |
|---|---|
| `:fs/read` `:fs/write` `:fs/edit` `:fs/multi_edit` `:fs/grep` `:fs/glob` | `fs__read` … |
| `:exec/run` | `exec__run` |
| `:web/fetch` `:web/search` | `web__fetch` `web__search` |
| `:memory/write` `:memory/get` `:memory/search` | `memory__write` … |
| `:session/info` `:session/model` | `session__info` `session__model` |
| `:skill/load` `:skill/list` | `skill__load` `skill__list` |
| `:comm/send` | `comm__send` |
| `:hail/send` `:hail/get` | `hail__send` `hail__get` |

`(name :fs/read)` is `"read"` — today’s allow matching is wrong. Canonicalize
with namespace: `fs__read`. Same helper in registry, turn, and status.

`activate-missing-tool!` looks up berth keys; berth ids become namespaced
keywords (`:fs/read` or a flattened id — pick one scheme in-agent and use
it consistently; wire name is still `fs__read`).

## Acceptance (`@wip` in isaac-agent)

`features/tool/allowlist.feature` (existing scenarios still use old wire
names; rewrite them as part of this bean):

- `bb features features/tool/allowlist.feature:109`
- `bb features features/tool/allowlist.feature:124`
- `bb features features/tool/allowlist.feature:136`
- `bb features features/tool/allowlist.feature:151`
- `bb features features/tool/allowlist.feature:175`
- `bb features features/tool/allowlist.feature:192`
- `bb features features/tool/allowlist.feature:205`

New steps invented: none. Promote to `todo` after these `@wip` scenarios
are committed.

## Out of scope

- Global vs crew overlay, `:deny`, `:all` semantics as a cascade (isaac-da0r).
- MCP servers.
- Back-compat aliases for `read` / `exec`.
