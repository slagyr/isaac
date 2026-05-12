---
# isaac-9phs
title: "Collocate module specs and features under modules/<id>/"
status: completed
type: task
priority: normal
created_at: 2026-05-05T18:54:32Z
updated_at: 2026-05-05T22:29:26Z
---

## Description

Why: Discord migrated source to modules/isaac.comm.discord/ but its specs and features stayed in core's spec/ and features/ trees. The same gap will hit telly once rlci ships, and any future module. Modules aren't real subprojects until their tests travel with their source.

Specifically, today:
- Discord specs live at spec/isaac/comm/{discord_spec.clj, discord/rest_spec.clj, discord/gateway_spec.clj}
- Discord features live at features/comm/discord/*.feature (11 files)
- Discord step defs live at spec/isaac/features/steps/discord.clj
- All of these reference internals of modules/isaac.comm.discord/, blurring the boundary

## Scope

- Move Discord's tests under the module:
  - spec/isaac/comm/discord*_spec.clj  -> modules/isaac.comm.discord/spec/isaac/comm/...
  - spec/isaac/features/steps/discord.clj -> modules/isaac.comm.discord/spec/isaac/features/steps/discord.clj
  - features/comm/discord/*.feature -> modules/isaac.comm.discord/features/comm/discord/*.feature
- Same for telly once rlci has moved its source.
- bb spec and bb features must walk modules/<id>/{spec,features}/ in addition to the root spec/ and features/ trees.
  - For specs: extend the spec discovery glob (-D arg or :paths) to include modules.
  - For features: the features task currently file-seq's features/; extend to also walk modules/*/features/.
- Step namespaces: step defs from modules/<id>/spec/isaac/features/steps/*.clj must register under the same isaac.features.steps.* namespace prefix the features task already passes via -s.

## Out of scope

- Dynamic classpath at activation time (isaac-sj3m) — tracked separately. This bead assumes :paths still includes module src/spec dirs at compile time.
- A formal module-testing convention doc (do later if useful).

## Acceptance

- modules/isaac.comm.discord/{spec,features}/ contains Discord's specs, features, and step defs
- core spec/ and features/ no longer reference Discord internals
- bb spec runs both core and module specs; total spec count is unchanged (or higher if new specs are added)
- bb features runs both core and module feature files; pre-existing Discord scenarios still pass
- Same migration playbook applies cleanly when rlci's telly cleanup lands

## Acceptance Criteria

Discord specs, features, and step defs live under modules/isaac.comm.discord/; bb spec and bb features walk modules/<id>/{spec,features}/ in addition to the core trees; pre-existing test counts/results unchanged

## Notes

Verification failed: bb spec and bb features both pass, Discord specs/features/step defs are present under modules/isaac.comm.discord/, and the root trees no longer contain spec/isaac/comm/discord* or features/comm/discord/*. However, the acceptance also says core spec/ and features/ no longer reference Discord internals, and root specs still do. Current examples: spec/isaac/module/loader_spec.clj requires isaac.comm.discord; spec/isaac/server/app_spec.clj requires isaac.comm.discord and isaac.comm.discord.gateway; spec/isaac/features/steps/session.clj requires isaac.comm.discord; spec/isaac/comm_spec.clj references isaac.comm.discord.rest and isaac.comm.discord/->DiscordIntegration. Because core spec/ still references Discord internals, the bead is not fully complete.

