---
# isaac-au48
title: "Replace bare top-level register-factory! calls with -isaac-init activation hook"
status: completed
type: task
priority: normal
created_at: 2026-05-06T17:06:23Z
updated_at: 2026-05-06T19:28:16Z
---

## Description

Why: today, modules register their factories as bare top-level side effects in their entry namespace:

  ;; modules/isaac.comm.discord/src/isaac/comm/discord.clj:304
  (comm/register-factory! "discord" make)

This conflates 'namespace loaded' with 'module activated'. Anyone requiring the namespace (tests, REPL exploration, classpath warmup) triggers registration as a surprise side effect. (require :reload) re-runs it. The registry can drift relative to the activator's intent.

Cleaner shape: the entry namespace defines a function — '-isaac-init' — and the activator calls it explicitly after require. Separation of 'load' (define vars) from 'activate' (run the side effects).

## Scope

- isaac.module.loader/activate! looks for <entry-ns>/-isaac-init after (require entry) and calls it if present. Convention over configuration; no manifest change.
- modules/isaac.comm.discord/src/isaac/comm/discord.clj — replace the bare register-factory! call with (defn -isaac-init [] (comm/register-factory! "discord" make))
- modules/isaac.comm.telly/src/isaac/comm/telly.clj — same shape
- Tests that currently rely on require-time registration need updating. Specifically, modules/isaac.comm.discord/spec/isaac/server/discord_app_spec.clj has 4 specs that fail when the bare call is removed — they assume Discord's factory is registered as soon as isaac.comm.discord is on the classpath. Those specs need to either call -isaac-init explicitly, drive activation via activate!, or stub the registration up-front.
- Lifecycle and module/loader specs already drive activation via reconcile!/activate! and should keep working since activate! will call -isaac-init.

## Why this is a real architectural improvement

- Tests can require the namespace without auto-registering. Isolation becomes default, not opt-in via *registry* binding.
- (require :reload) for code reload doesn't double-register or surprise the registry.
- Errors during init are localized to the activator's catch path, not entangled with require's load failure path.
- Same convention scales to providers, tools, hooks — any module-extensible registry.
- Sets up symmetric -isaac-stop / -isaac-deactivate hooks if/when deactivation lands.

## Acceptance

- modules/*/src/.../*.clj have no bare top-level register-factory! calls; registrations live inside -isaac-init.
- isaac.module.loader/activate! resolves and calls <entry>/-isaac-init after require.
- bb spec passes.
- bb features passes.
- discord_app_spec.clj's 4 currently-passing specs continue to pass after migration (drive registration via activate! or explicit -isaac-init call).

## Acceptance Criteria

No module ns has a bare top-level register-factory! call; activate! calls <entry>/-isaac-init after require; bb spec and bb features pass; discord_app_spec adapts cleanly

