---
# isaac-pl1x
title: 'Foundation pre-work: decouple module.loader from isaac.llm.api'
status: todo
type: task
created_at: 2026-06-12T12:48:48Z
updated_at: 2026-06-12T12:48:48Z
parent: isaac-brth
---

Phase A step 1 of the isaac-foundation extraction (seed model; see the
reshaping note in isaac-brth). Goal: zero foundation→server requires.

module.loader's ONLY use of isaac.llm.api is api/clear-module-registrations!
inside clear-activations! (src/isaac/module/loader.clj:302) — test-scenario
reset support.

- [ ] Spec first (spec/isaac/module/loader_spec.clj): clear-activations!
      invokes any registered :clear-registrations handlers; absent handlers
      are a no-op (don't reuse handler-for, which throws).
- [ ] Add multi-valued :clear-registrations support on the loader's existing
      register-handler! injection table (loader.clj:27-48).
- [ ] isaac.llm.api self-registers its clear-module-registrations! at
      namespace load (mirror config.loader's :user-config registration at
      config/loader.clj:1384).
- [ ] Delete [isaac.llm.api :as api] require from module/loader.clj.

## Acceptance

- bb spec and bb features green.
- rg 'isaac.llm' src/isaac/module/ returns zero hits.
