---
# isaac-ivk9
title: modules tree hides :isaac.server (platform-module-ids) despite real dependents + conflicts
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-06-19T19:24:48Z
updated_at: 2026-06-19T19:29:11Z
---

`modules list` never shows :isaac.server even though modules depend on it
(acp, hooks, ...), and server version conflicts are never detected.

## Root cause

platform-module-ids (src/isaac/module/loader.clj:66) =
  #{:isaac.foundation :isaac.server}
transitive-module-requirements (loader.clj:375) discovers platform modules but
EXCLUDES them from the surfaced set:
  (if (and module-id (not platform?)) (conj found module-id) found)
So server is resolved/loaded but filtered out of the tree. agent (not platform)
shows; server (platform) is hidden.

## Why this is wrong

foundation belongs in platform-module-ids — it's the singular SEED, excluded via
92p3's seed-authoritative path. server is NOT a seed: it's an optional,
versioned, registry-installable module like agent. Grouping it with foundation:
1. hides it from `modules list` despite real dependents (REQUIRED BY acp/hooks).
2. drops it from the resolved set yi82 compares -> server version conflicts
   (acp wants server@0.1.0, hooks@0.1.2) are NEVER warned. The conflict the user
   expected to see.

## Fix

Remove :isaac.server from platform-module-ids -> keep only :isaac.foundation.
server then surfaces as a normal transitive module (version + REQUIRED BY) and
gets conflict detection. NOTE: server owns the :isaac.server/* berth namespace
(why it may have been grouped as "platform") — confirm that's a CONTRIBUTION-slot
role, not a reason to hide it from the dependency tree. The seed-foundation-lib
exclusion (loader.clj:218) is foundation-only and separate, so removing server
from platform-module-ids does not change classpath/seed behavior.

## Acceptance

• A config whose modules depend on server -> `modules list` shows isaac.server
  with its version + REQUIRED BY.
• Two modules pinning different server versions -> conflict surfaces in the yi82
  conflicts table.

## Relationships

• Surfaced by isaac-90df (git-coord tree). Restores yi82 conflict detection for
  server. Pairs with isaac-wnyz (implied-coord display).
