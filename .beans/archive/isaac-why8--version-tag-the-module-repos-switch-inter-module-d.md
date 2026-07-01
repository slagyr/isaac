---
# isaac-why8
title: Version-tag the module repos; switch inter-module deps from :git/sha to tags
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-18T14:32:19Z
updated_at: 2026-06-18T18:06:00Z
---

Today only isaac-foundation has a version tag (v0.1.0). The other 8 modules — agent, server, acp, cron, hail,
hooks, discord, imessage — have NONE, and inter-module deps.edn reference each other by :git/sha. That is brittle
(every foundation change forces a SHA bump in every downstream repo) AND blocks a clean module registry
(modules.edn needs stable versioned coords).

## Goal
- Tag v0.x.0 on each of the 9 module repos.
- Switch inter-module deps.edn references from :git/sha to :git/tag.

## Approach
- Versioning scheme: v0.1.0 baseline; bump per release.
- Order by dependency: foundation (has v0.1.0) -> agent -> server/acp -> cron/hail/hooks -> discord/imessage.
  Tag a repo, push the tag, then bump its consumers' deps to that tag.
- Decide how tags get cut going forward: manual `git tag` + push, or a small release workflow per repo.

## Acceptance
- All 9 module repos have a version tag.
- Inter-module deps reference :git/tag (not :git/sha).
- Everything still resolves and bb ci is green across the repos.

## Why now
Prerequisite for the module registry (modules.edn) and the `isaac modules install` UX (isaac-dhzy). Also good
hygiene independent of the registry.

## Verification notes

- Verified on 2026-06-18 after pulling current `main` in `isaac`, `isaac-foundation`, `isaac-agent`, `isaac-server`, `isaac-acp`, `isaac-cron`, `isaac-hail`, `isaac-hooks`, `isaac-discord`, and `isaac-imessage`.
- All 9 module repos now have tag `v0.1.0`.
- Inter-module `deps.edn` references now use `:git/tag` with companion `:git/sha`; the earlier raw SHA-only references are gone.
- `bb ci` is green in `isaac-foundation`, `isaac-agent`, `isaac-cron`, and `isaac-hooks`.
- The remaining red runs in this verifier sandbox are environmental rather than product failures: `isaac-server` and `isaac-hail` fail feature startup on `java.net.SocketException: Operation not permitted` when binding HTTP sockets; `isaac-acp`, `isaac-discord`, and `isaac-imessage` fail classpath/dependency setup because the sandbox blocks writes under `~/.gitlibs` and `~/.clojure/.cpcache` for tagged git deps.
