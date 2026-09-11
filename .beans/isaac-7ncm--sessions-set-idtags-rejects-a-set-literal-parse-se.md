---
# isaac-7ncm
title: 'sessions set <id>.tags rejects a set literal: parse-set-value never EDN-reads #{…}; error should show the .tags.<keyword> form'
status: completed
type: bug
priority: normal
tags:
    - agent
    - cli
created_at: 2026-09-11T05:50:17Z
updated_at: 2026-09-11T06:17:07Z
---

Repo: **isaac-agent** (`src/isaac/session/cli.clj` `parse-set-value`, session schema `:tags` `:set-type? true`).

## Problem

`isaac sessions set <id>.tags "#{:isaac :ci}"` fails with "must be a set of
keywords" whatever you pass: `parse-set-value` EDN-reads values starting with
`[`, `{`, `:` or `"`, but a set literal starts with `#` and falls through as a
raw string; `":isaac :ci"` reads only the first keyword. The only working form
is one tag at a time via `sessions set <id>.tags.<keyword>` (mutation.feature),
which the error never mentions. Hit 2026-09-11 while provisioning isaac-work-3
and isaac-verify-2; worked around by editing session.edn by hand.

## Expected

- `sessions set <id>.tags #{...}` replaces the whole set (EDN set literal
  parsed; every element must be a keyword — existing validation).
- Any other unparseable value on a `:set-type?` field errors with a message
  that shows both forms: the set literal and `.tags.<keyword>`.

## Acceptance

isaac-agent a9381ad: `features/session/mutation.feature` — "sessions set
<id>.tags replaces the whole set from a set literal" (@wip). Add the
error-message scenario alongside (bad value → stderr names both forms).

```
cd isaac-agent
bb features features/session/mutation.feature features/session/cli.feature
bb spec spec/isaac/session
bb ci
```


## Handoff (scrapper@isaac-work-1)

branch: bean/isaac-7ncm @ a0ea41c (base origin/main@a9381ad)

Implemented EDN set-literal replacement for session tags, joined split CLI value tokens so unquoted `#{:isaac :ci}` reaches the parser whole, and added validation guidance naming both `#{:tag-1 :tag-2}` and `.tags.<keyword>` forms. Removed @wip and added the bad-input acceptance scenario.

Verified: mutation+CLI features 47/0; session specs 317/0. `bb ci` was run twice and is blocked by the unrelated/flaky `spec/isaac/tool/file_spec.clj:132` (passes alone: 37/0).



## Landed on main (2026-09-11)

main-sha: isaac-agent dcf0954d5b68bdaa191d244471b5dd04e72b6119
