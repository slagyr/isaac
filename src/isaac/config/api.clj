(ns isaac.config.api
  "The public interface to Isaac configuration. Everything outside the
   isaac.config.* namespaces requires *only* this namespace (aliased `config`)
   — never loader / install / mutate / configurator / change-source directly —
   so those internals stay free to reorganize behind this surface.

   Exceptions: isaac.config.paths and isaac.config.nav are dependency-free
   utilities (pure path construction / schema-path walking) that callers may
   require directly; they carry no config state or logic to hide.

   Each fn delegates to its source at call time, so `with-redefs` on the
   underlying fn still takes effect for callers through this API."
  (:require
    [isaac.config.change-source :as change-source]
    [isaac.config.configurator :as configurator]
    [isaac.config.install :as install]
    [isaac.config.loader :as loader]))

;; ----- loading & snapshot -----

(defn load-config
  "Loads config from `opts` and returns the resolved config map (the :config of
   load-config-result). `opts` keys: :state-dir, :fs, ..."
  [opts]
  (loader/load-config opts))

(defn load-config-result
  "Loads config and returns the full result map:
   {:config :errors :warnings :sources :missing-config?}.
   opts keys: :state-dir (required), :fs, :substitute-env?, :raw-parse-errors?,
   :skip-entity-files?, :data-path-overlay."
  ([]     (loader/load-config-result))
  ([opts] (loader/load-config-result opts)))

(defn normalize-config
  "Normalizes a raw config map into canonical shape (crew/models/providers,
   legacy forms migrated)."
  [cfg]
  (loader/normalize-config cfg))

(defn snapshot
  "Returns the current process-wide config snapshot, or nil if not yet set.
   An ambient read: call ONLY at entry points / wake boundaries; in-flight code
   must receive config as a value. `reason` documents why this site reads
   ambient config (kept greppable / reviewable)."
  [reason]
  (loader/snapshot reason))

(defn set-snapshot!
  "Installs the process-wide config snapshot. Set only at config-change
   boundaries — the install! coordinator does this at boot and on reload; direct
   use is for tests. `reason` documents the call site."
  [cfg reason]
  (loader/set-snapshot! cfg reason))

(defn state-dir
  "Returns the resolved state directory: a nexus-installed test override if
   present, otherwise the loaded config snapshot's :state-dir."
  []
  (loader/state-dir))

;; ----- env -----

(defn env
  "Resolves an environment variable name for ${VAR} substitution: an override
   first, then the process environment, then the loaded .env snapshot."
  [name]
  (loader/env name))

(defn set-env-override!
  "Sets an env-var override (test support). Clears the load cache."
  [name value]
  (loader/set-env-override! name value))

(defn clear-env-overrides!
  "Clears all env-var overrides and the .env snapshot (test support)."
  []
  (loader/clear-env-overrides!))

(defn clear-load-cache!
  "Clears the memoized load-config-result cache (test support / after writes)."
  []
  (loader/clear-load-cache!))

;; ----- resolution -----

(defn resolve-provider
  "Resolves the provider config for `provider-id` against `cfg`."
  [cfg provider-id]
  (loader/resolve-provider cfg provider-id))

(defn resolve-crew
  "Resolves the crew config for `crew-id` against `cfg`, merging in defaults."
  [cfg crew-id]
  (loader/resolve-crew cfg crew-id))

(defn resolve-crew-context
  "Resolves a crew's per-turn context (soul, model, provider, context-window).
   `opts` may carry crew-members/models/providers overrides and :model-override."
  ([cfg crew-id]      (loader/resolve-crew-context cfg crew-id))
  ([cfg crew-id opts] (loader/resolve-crew-context cfg crew-id opts)))

(defn resolve-history-retention
  "Resolves history retention for a new session from the cascade:
   explicit > crew > model > provider > defaults > default-history-retention."
  [cfg crew-id explicit-retention]
  (loader/resolve-history-retention cfg crew-id explicit-retention))

(def default-history-retention
  "History-retention used when nothing in the resolution cascade specifies one."
  loader/default-history-retention)

(defn server-config
  "Resolves the server settings (port, host, hot-reload, dev) from `config`."
  [config]
  (loader/server-config config))

;; ----- install (config -> nexus) -----

(defn install!
  "Populates the nexus from a loaded config map: sets the snapshot, ensures the
   session store and the [:tree] object-tree slot, then reconciles the given
   registries into it. opts keys: :config (required), :old-config (nil on boot),
   :registries, :host. Returns {:config :tree}."
  [opts]
  (install/install! opts))

;; ----- reconciliation (config -> live components) -----

(def Reconfigurable
  "Protocol implemented by config-driven components (comms, hail bands, hooks,
   cron) so the reconciler can start/stop/update them: on-startup! /
   on-config-change!."
  configurator/Reconfigurable)

(defn on-startup!
  "Reconfigurable method: called when an instance is first started with its
   config slice."
  [instance slice]
  (configurator/on-startup! instance slice))

(defn on-config-change!
  "Reconfigurable method: called on reload with the old and new config slices."
  [instance old-slice new-slice]
  (configurator/on-config-change! instance old-slice new-slice))

(defn reconcile!
  "Walks the registries' config slices and reconciles the live object tree
   against them. One fn for boot (old nil), reload (old vs new), and shutdown
   (new nil): [tree-atom host old-cfg new-cfg registry-or-registries]."
  [tree-atom host old-cfg new-cfg registry-or-registries]
  (configurator/reconcile! tree-atom host old-cfg new-cfg registry-or-registries))

(defn slot-impl
  "Resolves the component impl/type for a slot from its config slice."
  [slot slice]
  (configurator/slot-impl slot slice))

(defn ->name
  "Coerces a keyword or value to its string name (reconciler helper)."
  [x]
  (configurator/->name x))

;; ----- config change source (file watcher for hot reload) -----

(defn watch-service-source
  "Creates a filesystem-watching config change source rooted at `state-dir`."
  [state-dir]
  (change-source/watch-service-source state-dir))

(defn memory-source
  "Creates an in-memory config change source rooted at `state-dir` (test/dev)."
  [state-dir]
  (change-source/memory-source state-dir))

(defn start!
  "Starts a config change source. Returns the source."
  [source]
  (change-source/start! source))

(defn stop!
  "Stops a config change source."
  [source]
  (change-source/stop! source))

(defn poll!
  "Polls a config change source for the next changed config-relative path,
   waiting up to `timeout-ms` (default 0)."
  ([source]            (change-source/poll! source))
  ([source timeout-ms] (change-source/poll! source timeout-ms)))

(defn notify-path!
  "Notifies a config change source that `path` changed (test/dev)."
  [source path]
  (change-source/notify-path! source path))
