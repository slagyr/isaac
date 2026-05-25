(ns isaac.config.api
  "Public API for Isaac configuration. Clients require this namespace (aliased
   `config`) and use these functions instead of reaching into
   isaac.config.loader / install directly, leaving internal config namespaces
   free to reorganize behind this surface.

   Each fn delegates to its source at call time, so `with-redefs` on the
   underlying fn still takes effect for callers through this API."
  (:require
    [isaac.config.install :as install]
    [isaac.config.loader :as loader]))

;; ----- loading & snapshot -----

(defn load-config
  "Loads config from `opts` and returns the resolved config map (the :config of
   load-config-result). With no args, loads using loader defaults."
  ([]     (loader/load-config))
  ([opts] (loader/load-config opts)))

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
  "Returns the current process-wide config snapshot, or nil if not yet set."
  []
  (loader/snapshot))

(defn set-snapshot!
  "Sets the process-wide config snapshot. Called at boot and on every reload."
  [cfg]
  (loader/set-snapshot! cfg))

(defn state-dir
  "Returns the resolved state directory: a nexus-installed test override if
   present, otherwise the loaded config snapshot's :state-dir."
  []
  (loader/state-dir))

(defn env
  "Resolves an environment variable name for ${VAR} substitution: an override
   first, then the process environment, then the loaded .env snapshot."
  [name]
  (loader/env name))

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
