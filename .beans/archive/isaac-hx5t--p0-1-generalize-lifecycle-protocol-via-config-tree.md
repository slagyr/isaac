---
# isaac-hx5t
title: "P0.1 Generalize lifecycle protocol via config tree-walk reconciler"
status: completed
type: task
priority: normal
created_at: 2026-04-30T22:35:17Z
updated_at: 2026-05-03T16:46:42Z
---

## Description

The current isaac.plugin/Plugin protocol works (lifecycle.feature is
green) but it is the wrong shape for a plugin system:

- The protocol couples lifecycle to "config-path" as a method,
  forcing path-keyed registration.
- It lives in isaac.plugin — a namespace the upcoming plugin
  manifest system needs to claim (P1.x).
- It cannot express multiple instances of the same comm kind
  (e.g. two Discord clients at user-chosen paths).

Redesign as a config-driven reconciler.

## Model

Two parallel trees:

- Config tree — pure data, source of truth.
- Object tree — sparse twin of the config tree, holding stateful
  Lifecycle instances at the slots where they exist.

The reconciler keeps the object tree in sync with the config tree.

Owners (Lifecycle instances) manage everything BELOW their slot
themselves. The reconciler only dispatches at registered container
slots; owners who want to follow the same pattern internally can
do so with utilities exposed by isaac.lifecycle.

## Decisions

- Protocol home:       isaac.lifecycle/Lifecycle
- Object tree shape:   tree-shaped, mirrors config
- Owner scope:         owns its slot and below; reconciler only
                       sees the slot
- Registry isolation:  dynamic var with default global atom
                       (matches c3kit *ref-registry*)
- Factory signature:   (fn [host] -> Lifecycle); two-phase
                       (factory builds, on-startup! activates with slice)
- Reconciler API:      (reconcile! tree-atom host old-cfg new-cfg
                       registry); one fn for boot/reload/shutdown
- Dispatch key:        :impl
- Initial containers:  just :comms. Provider rework is its own
                       future task (Provider protocol flagged as
                       premature).

## Protocol

    (ns isaac.lifecycle)

    (defprotocol Lifecycle
      (on-startup!        [this slice])
      (on-config-change!  [this old-slice new-slice]))    ;; new=nil = stop

## Registry

    (ns isaac.comm.registry
      (:require [isaac.lifecycle :as lifecycle]))

    (def ^:dynamic *registry*
      (atom {:path  [:comms]
             :impls {}}))                ;; impl-name -> factory or :unbound

    (defn register-name! [impl-name]
      (swap! *registry* update :impls
             (fn [impls] (update impls impl-name #(or % :unbound)))))

    (defn register-factory! [impl-name factory]
      (swap! *registry* assoc-in [:impls impl-name] factory))

Tests isolate via:

    (binding [comm-registry/*registry* (atom {...})] ...)

## Two-phase registration

To support config validation without loading plugin code, the
registry has two registration steps:

1. **Manifest discovery** registers the impl NAME. After this,
   :impls contains {<name> :unbound}. This is enough for config
   validation: a slot's :impl must be a registered name. Plugin
   code is NOT loaded yet.

2. **Plugin activation** registers the FACTORY function. The
   :unbound placeholder is replaced with the factory. Reconciler
   needs the factory; manifest validation does not.

For built-in comms (Discord today, others later) both steps happen
at namespace-load: the namespace is on classpath and self-registers
both name and factory in one go. For third-party plugins, name
comes from plugin.edn :extends, factory comes from on-activation
require.

This dependency on the manifest system means hx5t lands first
(against current built-ins; both phases collapsed) and the
two-phase split takes effect when the manifest system arrives
(P1.x).

## Reconciler

    (reconcile! tree-atom host old-cfg new-cfg registry)

For each user-chosen slot under the registry's path:

- new-impl present, no instance → factory → place → on-startup! slice
- both present, slice changed → on-config-change! old new
- old-impl present, new gone → on-config-change! old nil, evict
- :impl changed (e.g. discord → gchat) → teardown old, build new,
  startup new

host is {:state-dir ... :name <user-chosen-slot-key> ...}. Each
impl can dock additional context (Discord wants :connect-ws!).

Server lifecycle:

- Boot:    (reconcile! tree host nil  cfg  comm-registry)
- Reload:  (reconcile! tree host old  new  comm-registry)
- Stop:    (reconcile! tree host @cfg nil  comm-registry)

## Multi-instance

Two Discord configs at user-chosen names work natively:

    {:comms {:ander {:impl "discord" :token "A" ...}
             :plex  {:impl "discord" :token "B" ...}}}

Reconciler instantiates two DiscordIntegration objects. Each gets
its own slot in the object tree and :name ("ander" / "plex") in host.

## Validation

A slot's :impl must be a name registered in the comm registry.
Validation runs at config-load time (before any reconcile) and
fails the server boot with a structured error if any slot
references an unknown :impl. See the validation scenario for
exact log shape.

## Feature scenarios

features/lifecycle/reconciler.feature (new). Five scenarios:

1. Two comms run independently (multi-instance start)
2. Comm state updates when its slice changes (slice change)
3. Comm is removed when its slot is removed from config (stop)
4. Unregistered :impl is a config validation error (validation)
5. Comm is hot-added when its slot appears in config (hot-add)

A "telly" test comm impl (registered only in tests via a
spec-side namespace) drives all four — keeps the reconciler
spec independent of Discord. Slot names use Sesame Street
characters (bert, ernie, elmo, abby).

Required new step phrases:

- the "{impl}" comm is registered
- the comm "{name}" exists with state:    (table)
- the comm "{name}" does not exist
- config is updated:                      (table; delta-merge)
- the Isaac server is started             (attempts; doesn't assert)
- the Isaac server is not running         (asserts no live server)

Required new value marker:

- "#delete" in a config-update value cell removes the key
  (depends on isaac-puhp; coordinate impl)

Required new log events:

- :lifecycle/started       fields: :impl :path
- :lifecycle/changed       fields: :impl :path
- :lifecycle/stopped       fields: :impl :path
- :config/validation-error fields: :path :message

## Migration

- Move DiscordIntegration (post-P0.0) registration from
  (plugin/register! ...) to
  (comm-registry/register-factory! "discord" ...). Built-in path
  collapses both registration phases into one ns-load.
- Production cfg: today's :comms.discord stays as a single-instance
  shorthand OR requires explicit :impl "discord". Decide during
  implementation; default to requiring :impl for v1 unless a clean
  shorthand falls out.
- Server start!/sync-config!/stop! call reconcile! once per container.
- Delete isaac.plugin namespace; free for plugin manifest (P1.x).
- lifecycle.feature scenarios stay green through migration
  (regression target).

## Acceptance

- isaac.lifecycle/Lifecycle protocol exists with on-startup!,
  on-config-change!.
- isaac.comm.registry/*registry* is a dynamic var; register-name!
  and register-factory! mutate the bound atom.
- isaac.lifecycle/reconcile! handles boot, reload, shutdown via
  one fn.
- Object tree is held by the server as an atom and mirrors config
  tree shape under :comms.
- Discord registers as kind "discord"; multi-instance scenario
  (two slots, both impl "discord") spins up two independent
  integrations.
- All 5 reconciler.feature scenarios pass without @wip.
- All 4 existing lifecycle.feature scenarios stay green.
- isaac.plugin namespace deleted; no (:require [isaac.plugin])
  remains in the codebase.
- :impl change at runtime triggers teardown-then-startup.
- Cfg referencing an unregistered :impl fails server boot with a
  :config/validation-error log entry.

## Doesn't include

- Plugin manifest system (P1.x). hx5t's two-phase registration is
  designed for it but doesn't require it; built-ins collapse both
  phases.
- Generalizing other containers (tools, providers).
- Provider protocol redesign (premature; flagged for separate work).
- Owner-internal subtree management utilities (separate bead
  if/when needed).

## Depends on

- P0.0 (DiscordIntegration is one type — designed against the clean
  Discord, not the current two-deftype shape).

## Notes

Implementation complete. All 5 reconciler.feature scenarios pass. All 4 existing lifecycle.feature scenarios stay green. isaac.plugin namespace deleted. Discord registered via comm-registry/register-factory!. Slot-name shorthand: when :impl is missing, slot key is used as default (lets existing :comms.discord cfg work without explicit :impl). Multi-instance works (test uses 'telly' impl with two slots). Validation: unknown :impl logs :config/validation-error and aborts boot. 1185 unit specs + 477 features pass.

