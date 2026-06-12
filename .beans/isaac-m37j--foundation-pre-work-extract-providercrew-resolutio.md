---
# isaac-m37j
title: 'Foundation pre-work: extract provider/crew resolution into isaac.config.resolve'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-12T12:49:31Z
updated_at: 2026-06-12T15:05:40Z
parent: isaac-brth
blocked_by:
    - isaac-ptkg
---

Phase A step 5 of the isaac-foundation extraction (see isaac-brth reshaping
note). config/loader.clj requires isaac.llm.provider and isaac.llm.providers
(loader.clj:15-16), used solely by the resolution functions. Extract them
server-side so the loader (foundation set) drops its llm requires.

- [ ] Run the scrap skill on spec/isaac/config/loader_spec.clj (1336 lines)
      before carving.
- [ ] New server ns src/isaac/config/resolve.clj: resolve-provider,
      parse-model-ref, resolve-crew, resolve-crew-context,
      resolve-history-retention, default-history-retention,
      apply-model-override + helpers, server-config. It may require the
      foundation loader (normalize-config, resolve-workspace,
      read-workspace-file stay foundation — they have no server requires).
- [ ] Re-point callers (all server: charge, session/context, server/app,
      session/store/*, llm/providers) — either require isaac.config.resolve
      directly or via isaac.config.runtime wrappers.
- [ ] Delete [isaac.llm.provider] and [isaac.llm.providers] from
      config/loader.clj.
- [ ] Split the matching describe blocks out of loader_spec into
      spec/isaac/config/resolve_spec.clj.

## Acceptance

- bb spec and bb features green.
- rg 'isaac.llm' src/isaac/config/loader.clj returns zero hits.
