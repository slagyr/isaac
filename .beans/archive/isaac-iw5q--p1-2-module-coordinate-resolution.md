---
# isaac-iw5q
title: "P1.2 Module coordinate resolution"
status: completed
type: task
priority: normal
created_at: 2026-04-30T22:36:04Z
updated_at: 2026-05-04T23:32:04Z
---

## Description

Resolve a module id to a directory on disk. Per the design,
coordinates are just ids — Isaac doesn't fetch.

## Architecture

- Pure path resolution, no fetching, no manifest parsing.
- Caller (discovery / cccs) reads module.edn from the resolved path.
- Routes through isaac.fs so it works with memfs.

## Search order (closest wins)

1. <state-dir>/modules/<id>/   — third-party (user-installed; closer)
2. modules/<id>/                — cwd-relative built-in

First found wins. No warning on shadowing — the third-party
override is the intended UX.

## Id validation

Module id is a Clojure keyword. The (name id) string is used
verbatim as the directory segment. Validate with regex:

  [a-zA-Z0-9._-]+

Anything else fails with a structured error before resolution.
No munging — keywords are restricted enough that (name id) is
filesystem-safe by construction once validated.

## Returns

- Existing directory: string path (works with isaac.fs).
- Neither location exists: nil. Caller decides whether to error
  (cccs hard-errors with a clear message; other callers may
  tolerate).

## Where it lives

isaac.module.coords. Public fns: worker decides exact surface
(probably `resolve` and a `candidates` for error messages, plus
`valid-id?`).

## Out of scope

- Jar/classpath resolution. Defer until Isaac is packaged as a
  jar. Separate bead.
- Fetching from the network (HTTP/git). User installs by dropping
  files at <state-dir>/modules/<id>/.
- Reading or parsing module.edn (cccs's job).

## Tests

Unit specs only. Cover:

- Valid id resolves to closest path
- Third-party shadows built-in
- Returns nil when neither exists
- Invalid id (bad chars) fails fast with structured error
- memfs and real fs both work

User-observable behavior tested via fk45's
features/module/manifest.feature once the chain is wired.

## Depends on

- P1.1 (manifest format defines :id grammar; specifically that
  the id is a keyword whose (name) is the canonical filesystem
  segment)

## Notes

Implemented isaac.module.coords with valid-id?, candidates, and resolve. Search order prefers <state-dir>/modules/<name id> over <cwd>/modules/<name id>, validates ids with [a-zA-Z0-9._-]+, and returns nil when neither candidate is a directory. Added unit specs for memfs and real fs. Verified with bb spec and bb features.

