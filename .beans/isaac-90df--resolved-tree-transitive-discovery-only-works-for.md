---
# isaac-90df
title: Resolved-tree transitive discovery only works for :local/root, not git/mvn coords
status: todo
type: bug
priority: high
created_at: 2026-06-19T18:18:21Z
updated_at: 2026-06-19T18:18:21Z
---

On a real (git-coord) install, `modules list` shows ONLY explicit modules with
EMPTY required-by; transitive modules never surface and version conflicts are
never detected — so isaac-0yp1's resolved tree AND isaac-yi82's conflict warning
are effectively non-functional in production.

## Observed (zanebot, v0.1.2)

`modules list` shows the 5 configured comm/cron/hail modules, no REQUIRED BY,
no agent/server (both ship manifests, both are pulled via the comms' deps.edn),
and no conflict warning — even though comms pin agent/server v0.1.0 and
hail/cron pin v0.1.2 (a genuine conflict).

## Root cause

coord-directory (src/isaac/module/loader.clj:146) resolves ONLY :local/root:
  (when-let [root (:local/root coord)] ...)
Returns nil for git/mvn coords. read-module-deps-edn (loader.clj:318) and
transitive-module-requirements (loader.clj:336) both depend on it, so a git-coord
module yields no deps.edn read -> no transitive walk -> the tree degrades to a
flat config-only list. (Runtime classpath compose still pulls the deps via
tools.deps; only the LIST/tree discovery is broken.)

## Why 0yp1 / yi82 didn't catch it

Every 0yp1 + yi82 fixture is :local/root marigold modules — the ONLY case
coord-directory handles. Green tests never exercised git coords. (Flagged at
0yp1 completion: local-root tests don't prove real behavior.)

## Fix

coord-directory must resolve git coords to their on-disk location (tools.deps
~/.gitlibs/libs/<lib>/<sha>/) and mvn to ~/.m2, so deps.edn is readable for
transitive discovery — reuse the resolution the loader already does at
classpath-compose time (the coord is cloned then), or read deps.edn from the
resolved basis.

## Acceptance

• A feature test with GIT-coord fixtures (not :local/root): a published module
  pulling another manifest-bearing module via git -> tree surfaces it REQUIRED
  BY; and a git-coord version conflict surfaces in the yi82 conflicts table.
• Add git-coord coverage to the @slow suite (isaac-09zr).

## Relationships

• Breaks isaac-0yp1 (tree) + isaac-yi82 (conflicts) on real installs.
• The missing git/versioned coverage 92p3/0yp1/yi82 fixtures all lack.
