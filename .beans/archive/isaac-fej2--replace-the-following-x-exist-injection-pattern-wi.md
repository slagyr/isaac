---
# isaac-fej2
title: "Replace 'the following X exist' injection pattern with real disk config"
status: completed
type: task
priority: normal
created_at: 2026-04-23T15:59:33Z
updated_at: 2026-04-23T18:42:25Z
---

## Description

Current test infrastructure lets features set config via atoms:

  And the following models exist: ...
  And the following crew exist:  ...

These populate g/get :crew / :models, which cli.clj:53 and server.clj:138 inject as extra-opts, bypassing config/load-config entirely. That hides whatever disk-loading behavior really does, makes config-related features hard to test (e.g. hot-reload), and has an implicit 'which atom wins' merge rule.

Replace with file-based setup using the existing 'the file X exists with:' step (pending rename under isaac-85le):

  And the file "target/test-state/config/models/grover.edn" exists with:
    '''
    {:model "echo" :provider "grover" :context-window 32768}
    '''

Changes:
- Step defs to retire: models-exist, agents-exist, crew-exist (session.clj:253-280). Any providers-exist variant too if present.
- Keep: sessions-exist (sessions are state, not config), files-exist (generic FS), file-with-content, etc.
- the Isaac server is running (server.clj:138): stop merging g/get :crew into cfg; always run config/load-config against state-dir.
- isaac is run with (cli.clj:40-67): stop injecting :agents/:crew/:models as extra-opts. Let the command load config from disk like production.
- Update all 65 feature files to use the file-based setup.

Scope note: this is a mechanical migration but large. Can be split into passes (e.g., one feature dir at a time) if a single PR is too much.

Related:
- isaac-85le shortens 'state file' step phrasing + allows state-dir-relative paths; land that first so rewritten features use short paths.
- This bead unblocks config hot-reload scenarios (no need to work around the injection pattern) but doesn't block them — hot-reload can land with file-based Background today.

Acceptance:
1. grep -rn 'the following crew exist\|the following models exist\|the following agents exist' features/ spec/ returns no matches
2. session.clj no longer exports models-exist, agents-exist, crew-exist steps
3. server.clj and cli.clj no longer inject from g/get :crew or :models
4. bb features and bb spec pass

## Notes

Reopened issue addressed: spec/isaac/features/steps/server.clj no longer merges g/get :crew or g/get :models into server-config in the Isaac server is running step. Verified again with bb features and bb spec; both pass, and retired injection step text remains absent.

