---
# isaac-e0t7
title: 'Session tags stored as a vector never match a selector: tags-of + contains? is index lookup'
status: todo
type: bug
priority: high
created_at: 2026-09-20T07:21:23Z
updated_at: 2026-09-20T07:21:23Z
---

`isaac.session.store.spi/tags-of` returns whatever is on the session and
`has-tag?` calls `contains?` on it. With a set (`#{:ops}`) that is membership;
with a **vector** (`[:ops]`) `contains?` asks whether index `:ops` exists —
always false. A session whose tags were written as a vector therefore matches
no tag selector at all: not hail, not `isaac.session.frequencies`, not the
gchat space entries added by isaac-tund. It fails silently — the selector
simply finds nothing and falls back to creating or to a default.

Found 2026-09-20 writing isaac-tund's scenarios: `| ops-room | main | [:ops] |`
created a session with vector tags and the selector never saw it; `#{:ops}`
worked. Both spellings are accepted on the way in, so nothing warns.

This is the same sharp edge as [[config-set-member-paths]] — Micah hit it
before from the CLI side ("sessions set .tags can't take a set, edit
session.edn by hand"). A session on disk with `:tags [:isaac]` is invisible to
every band.

Work: normalize tags to a set of keywords wherever they are read (`tags-of`)
and wherever they are written (`open-session!`, `sessions set`, the feature
steps' session table). Scenario: a session created with vector tags is
selected by `--tags`, by hail, and by a gchat space entry. Then check what is
on disk on zanebot and yopp.
