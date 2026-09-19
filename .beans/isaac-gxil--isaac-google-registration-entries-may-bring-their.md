---
# isaac-gxil
title: 'isaac-google: registration entries may bring their own :remote view and :delete! (for registrations Google cannot list)'
status: in-progress
type: feature
priority: high
tags:
    - google
    - unverified
created_at: 2026-09-19T19:18:33Z
updated_at: 2026-09-19T19:18:33Z
parent: isaac-bv1l
blocking:
    - isaac-12iz
---

The isaac-google half of isaac-12iz, split out so it lands first (isaac-gmail pins it).

Google lists Workspace Events subscriptions but not Gmail watches — users.watch only answers historyId + expiration. So an :isaac.google/registration entry may carry two optional hooks: `:remote` (fn [] -> {key {:name :expires-at}}; Gmail reads the timer's own persisted google/registrations.edn) and `:delete!` (fn [name]; Gmail = users.stop). Entries without them keep the Workspace Events listing and delete, so Chat is unchanged. Berth schema and description updated. Version 0.1.4.

Unit (spec/isaac/google/registration_spec.clj, done): an entry with :remote/:delete! creates once and remembers the expiry; renews through :renew! inside the window; stops through :delete! when its key leaves config.

## Acceptance
    cd isaac-google && bb ci     # 45 spec, 19 feature examples, 0 failures

## Handoff / resume
Planner finished locally (2026-09-19). branch: bean/isaac-12iz @ ac7a5b3 (base origin/main@4ece452) in isaac-google — fast-forward from main. Branch is named for 12iz; verify lands it as this bean. isaac-gmail's bean/isaac-12iz pins google at ac7a5b3 and is repinned by the planner to the landed main sha before 12iz is handed to verify.
