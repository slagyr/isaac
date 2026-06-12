---
# isaac-youm
title: 'Foundation pre-work: foundation boundary gate spec'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-12T12:50:49Z
updated_at: 2026-06-12T16:29:48Z
parent: isaac-brth
blocked_by:
    - isaac-0cc4
    - isaac-wop6
    - isaac-t7mq
    - isaac-mdv0
---

Phase A step 11 — the acceptance test for the whole decoupling pre-work and
the permanent guard during/after the cut (see isaac-brth reshaping note).

- [x] New spec (extend spec/isaac/module/layout_spec.clj or add
      spec/isaac/foundation_boundary_spec.clj): parse the ns forms of the
      foundation file set and assert every isaac.* require stays inside the
      set.

Foundation file set: isaac.{main,cli,core,module,system,nexus,fs,logger,root,
version,naming,scheduler,spec-helper,features-main},
isaac.module.{loader,manifest}, isaac.scheduler.cron,
isaac.schema.{dynamic,lexicon,meta,registered-in},
isaac.config.{paths,nav,companion,loader,api,berths,schema-base},
isaac.cli.{color,table}.

- [x] Confirm with greps that the set is clean of
      isaac.{server,session,llm,comm,bridge,hail,tool,slash,drive,cron,crew,
      hooks,prompt,service,charge,api,util} requires.

This bean is done only when the gate passes against the real tree — i.e. all
other Foundation pre-work beans have landed.

## Acceptance

- bb spec green including the new gate spec.

## Summary of Changes

Added the foundation boundary gate (spec/isaac/foundation_boundary_spec.clj):
parses the ns form of every namespace in the foundation file set and asserts
(a) each isaac.* require stays inside the set (closed) and (b) none match a
forbidden server-side prefix (server/session/llm/comm/bridge/hail/tool/slash/
drive/cron/crew/hooks/prompt/service/charge/api/util). The "confirm clean of
forbidden requires" item is implemented as a spec assertion (stronger than a
grep — permanent guard). Negative-tested: injecting isaac.llm.api into a
foundation file turns both checks red with a clear {ns [leaked-nses]} message.

Foundation set = the bean's 30 enumerated namespaces + the 3 foundation config
namespaces config.loader transitively requires (check-compose, schema-compose,
validation — created by the config schema/check pre-work), so the set is closed.

Leak found + fixed (folded in per the bean-session decision): the gate revealed
that foundation config.api transitively pulled isaac.llm (config.resolve ->
isaac.llm.provider) and isaac.session (config.resolve -> config.schema ->
isaac.session.compaction-schema) into the foundation. config.api's resolve
surface (resolve-provider/crew/crew-context/history-retention/
default-history-retention/server-config) is server-only — every caller is server
code. Removed those re-exports + the [isaac.config.resolve] require from
config.api; re-pointed the server callers (session/store/{memory,impl_common},
session/context, server/app, charge, and the server-side step nses) to require
isaac.config.resolve directly. config.api now requires only loader/paths/root.

The gate now passes against the real tree — all foundation pre-work has
effectively landed. bb spec 1900 green incl. the gate; bb features 744 green.
