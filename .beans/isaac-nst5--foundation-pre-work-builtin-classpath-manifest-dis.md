---
# isaac-nst5
title: 'Foundation pre-work: builtin classpath-manifest discovery (:builtin? + builtin-index)'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:49:38Z
updated_at: 2026-06-12T13:31:54Z
parent: isaac-brth
---

Phase A step 6 of the isaac-foundation extraction (see isaac-brth reshaping
note). New small mechanism that makes the eventual cut a pure file move: the
loader discovers ALL classpath manifests flagged :builtin? true, not just
:isaac.core. In-repo, the upcoming :isaac.server manifest is auto-discovered;
in the foundation repo nothing is flagged, so behavior is foundation-only.

- [ ] Add :builtin? to isaac.module.manifest (manifest-schema +
      known-meta-keys, src/isaac/module/manifest.clj:13-37).
- [ ] In module.loader: builtin-index = core-index merged with all classpath
      isaac-manifest.edn resources whose manifest has :builtin? true (reuse
      resource-urls / read-manifest-edn; cache alongside core-index-cache).
- [ ] *core-index-override* overrides the WHOLE builtin set (keeps
      spec/isaac/marigold.clj themed runs server-free).
- [ ] discover! merges builtin-index (as {id {}} implicit coords) instead of
      {core-module-id {}} (loader.clj:785).
- [ ] Switch core-index merge sites to builtin-index:
      config/loader.clj:527,547,599,736,958, config/cli/schema.clj:44,51,
      server/app.clj:165, llm/provider.clj:86.
- [ ] TDD with a flagged fixture manifest under a spec resource dir.

With nothing flagged in src/, behavior is identical — suite stays green.

## Acceptance

- bb spec and bb features green.
- A spec proves a :builtin? true classpath manifest is merged into discovery
  and a non-flagged one is not.
