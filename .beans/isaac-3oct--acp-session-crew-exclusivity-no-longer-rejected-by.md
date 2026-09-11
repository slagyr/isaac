---
# isaac-3oct
title: ACP --session + --crew exclusivity no longer rejected by frequencies_cli
status: draft
type: bug
priority: normal
created_at: 2026-08-29T14:42:46Z
updated_at: 2026-08-29T14:42:46Z
---

Repo: **isaac-agent** (`src/isaac/session/frequencies_cli.clj`), possibly **isaac-acp** `features/comm/acp/cli-resume.feature`.

Split from isaac-6yg0 pin-bump: ACP scenario `--session combined with selection flags is rejected per shared rules` expected `--session is mutually exclusive with --crew`. Current `validate-frequencies-options` only rejects `--session` with `--session-tag`/`--create` (and `--resume` with selection flags). `--session` + `--crew` is now a valid attach (crew as filter/override).

isaac-acp `features/comm/acp/cli-resume.feature` scenario is `@wip` on `bean/isaac-6yg0`. Either restore the exclusivity in frequencies_cli or rewrite the ACP scenario to the current shared rules.

Do not reopen isaac-6yg0 for this.
