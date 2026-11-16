---
# isaac-tihz
title: 'foundation: cli-forwarder registration throws on the server''s second discovery pass (error on every boot)'
status: draft
type: bug
priority: normal
tags:
    - cli
created_at: 2026-11-16T19:46:40Z
updated_at: 2026-11-16T19:46:40Z
---

Repo: **isaac-foundation** (`src/isaac/foundation/cli/registry.clj` `register-forwarder!`). Found on the 2026-11-16 server-only train (foundation 0.1.30, cli-proxy 0.2.0), in the zanebot rehearsal and again on the live boot.

## Symptom

Every server boot logs `:level :error :event :module/activation-failed :module "isaac.cli-proxy" :berth ":isaac/cli-forwarder" :error "Only one :isaac/cli-forwarder may be registered"`. Nothing breaks: the server never forwards (embedded dispatch), and forwarding from CLI processes works. But a boot should be clean, and a config hot-reload that re-activates modules would hit it again.

## Cause

`register-forwarder!` is a compare-and-set on a process-global atom. The server process discovers modules twice (the launcher's early CLI discovery pass, then the runtime module lifecycle), so cli-proxy's forwarder registers twice and the second throws.

## Direction

Registration is idempotent per contributing module: the same module re-registering replaces its forwarder (or the registry resets per discovery pass); only a *different* module contributing a second forwarder is the error. Deactivating the module unregisters it.

## Acceptance (draft; needs a scenario that reproduces the double discovery)

- A server boot with isaac-cli-proxy installed logs no `:module/activation-failed` (foundation `the log has no entries matching:` step exists), through the same entry point the launchd unit uses, not only `app/start!`.
- Two different modules contributing forwarders still fail with the existing message.
