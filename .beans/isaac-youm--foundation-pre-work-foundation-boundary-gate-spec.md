---
# isaac-youm
title: 'Foundation pre-work: foundation boundary gate spec'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:50:49Z
updated_at: 2026-06-12T16:11:00Z
parent: isaac-brth
blocked_by:
    - isaac-0cc4
    - isaac-wop6
    - isaac-t7mq
    - isaac-mdv0
---

Phase A step 11 — the acceptance test for the whole decoupling pre-work and
the permanent guard during/after the cut (see isaac-brth reshaping note).

- [ ] New spec (extend spec/isaac/module/layout_spec.clj or add
      spec/isaac/foundation_boundary_spec.clj): parse the ns forms of the
      foundation file set and assert every isaac.* require stays inside the
      set.

Foundation file set: isaac.{main,cli,core,module,system,nexus,fs,logger,root,
version,naming,scheduler,spec-helper,features-main},
isaac.module.{loader,manifest}, isaac.scheduler.cron,
isaac.schema.{dynamic,lexicon,meta,registered-in},
isaac.config.{paths,nav,companion,loader,api,berths,schema-base},
isaac.cli.{color,table}.

- [ ] Confirm with greps that the set is clean of
      isaac.{server,session,llm,comm,bridge,hail,tool,slash,drive,cron,crew,
      hooks,prompt,service,charge,api,util} requires.

This bean is done only when the gate passes against the real tree — i.e. all
other Foundation pre-work beans have landed.

## Acceptance

- bb spec green including the new gate spec.
