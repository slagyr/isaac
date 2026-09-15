---
# isaac-784x
title: 'modules upgrade validates new coords before fetching them: unfetched shas fail with bogus comm type errors'
status: draft
type: bug
priority: high
tags:
    - foundation
    - modules
    - deploy
created_at: 2026-09-15T18:31:56Z
updated_at: 2026-09-15T18:31:56Z
---

## Problem

`isaac modules upgrade <id>` refuses to upgrade to a coordinate that is not already checked out, printing misleading downstream validation errors.

Seen on zanebot 2026-09-15 (agent 0.1.68 → 0.1.69, `3e3ef7e`): `isaac modules upgrade isaac.agent` printed

```
error: comms[:discord] - unknown :type "discord"
error: comms[:imessage] - unknown :type "imessage"
```

and left `:modules` unchanged. `isaac config validate` on the same config reported no comm errors. The same kind of upgrade worked at 17:53Z earlier that day only because `isaac modules install` ran first and its validation checked out the new shas.

Cause, isaac-foundation `src/isaac/modules/cli.clj` `run-upgrade`:
- `mutate-modules!` (→ `mutate/set-config root "modules" merged :skip-ref-validation? true :skip-module-validation? true`) validates the whole config against the NEW coordinates first (line ~471).
- `loader/warm-module-checkouts!` fetches those coordinates only afterwards (line ~473), and only when the mutation succeeded.
- With the new agent sha not on disk, discovery cannot load the agent's comm berth, so comm types contributed by other modules (`discord`, `imessage`) become "unknown :type". `:skip-module-validation?` strips only `module-discovery-error?` errors, not these downstream errors, so the write is refused.

Workaround used: pre-fetch the gitlib on the host (`clojure -Sdeps '{:deps {isaac.agent/isaac.agent {:git/url … :git/sha …}}}' -P`), then re-run `isaac modules upgrade`.

## Proposal

- Warm (fetch) the upgraded coordinates before validating the mutated config, or
- Treat validation errors caused by an unfetched coordinate as module-discovery errors so `:skip-module-validation?` covers them, then warm after the write.

## Acceptance (draft — scenarios TBD)

- Upgrading a registry module to a sha that is not yet checked out succeeds and prints "Upgraded <id>: <old> -> <new>".
- A genuinely invalid config still blocks the upgrade with its real errors.
