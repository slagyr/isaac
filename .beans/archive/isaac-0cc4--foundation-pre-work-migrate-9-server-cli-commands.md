---
# isaac-0cc4
title: 'Foundation pre-work: migrate 9 server CLI commands to manifest :cli contributions'
status: completed
type: task
priority: normal
created_at: 2026-06-12T12:49:14Z
updated_at: 2026-06-12T14:52:31Z
parent: isaac-brth
blocked_by:
    - isaac-pl1x
    - isaac-6q8c
---

Phase A step 3 of the isaac-foundation extraction (see isaac-brth reshaping
note). isaac.main side-loads 9 server CLI namespaces (src/isaac/main.clj:14-22)
so their top-level registry/register! calls fire. These must become :cli berth
contributions in src/isaac-manifest.edn (alongside init) so isaac.main's
requires end up foundation-only. The greeter module
(modules/isaac.cli.greeter) is the working model; register-module-cli-commands!
(main.clj:44-64) already registers all :cli contributions on every invocation.

One micro-step per command — manifest entry carrying :run-fn / :option-spec /
:help-text symbols (symbol-valued option-specs already resolve, cli.clj:200;
service needs :subcommands from the prior bean), delete the namespace's
top-level register! form, delete its require from isaac.main:

- [ ] auth (isaac.llm.auth.cli) — keep "models auth" alias working
- [ ] config (isaac.config.cli.command) — has :help-text
- [ ] crew (isaac.crew.cli)
- [ ] hail (isaac.hail.cli) — has :help-text
- [ ] prompt (isaac.bridge.prompt-cli)
- [ ] logs (isaac.logs.cli)
- [ ] server (isaac.server.cli) — keep "gateway" alias working
- [ ] service (isaac.service.cli) — uses :subcommands
- [ ] sessions (isaac.session.cli)

Note: requiring-resolve of run-fn symbols loads the same namespaces main
loaded statically — no startup regression; lazy resolution is a possible
later optimization, not this bean.

## Acceptance

- bb spec and bb features green after EACH micro-step.
- isaac.main's :require block contains no isaac.{llm,config.cli,crew,hail,bridge,logs,server,service,session} namespaces.
- features/cli usage listing, per-command help, and aliases unchanged.
