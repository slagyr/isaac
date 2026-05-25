(ns isaac.config.install
  "Single coordinator for turning config into runtime state. Loads config and
   populates the nexus: the config snapshot, the session store, the live
   component tree (nexus [:tree]), and the config-driven components reconciled
   into it. Entry points call this instead of building config and installing
   components ad hoc.

   The registries to reconcile are injected by the caller so this namespace
   stays free of dependencies on the component implementations (comms, hail,
   hooks, cron)."
  (:require
    [isaac.config.loader :as config]
    [isaac.config.configurator :as configurator]
    [isaac.nexus :as nexus]
    [isaac.session.store :as store]))

(defn- ensure-tree! []
  (or (nexus/get :tree)
      (let [tree (atom {})]
        (nexus/register! [:tree] tree)
        tree)))

(defn- ensure-store! [config]
  (when-not (store/registered-store)
    (when-let [state-dir (:state-dir config)]
      (store/register! config state-dir))))

(defn install!
  "Populate the nexus from an already-loaded config map: set the snapshot,
   ensure the session store and the [:tree] object-tree slot, then reconcile the
   given registries' config slices into the tree.

   opts:
     :config      - loaded config map (required)
     :old-config  - previous config for the reconcile diff (nil on boot)
     :registries  - configurator registries to reconcile (default none)
     :host        - host context for reconcile! (module-index, connect-ws!, ...)
   Returns {:config config :tree tree}."
  [{:keys [config old-config registries host]}]
  (config/set-snapshot! config)
  (ensure-store! config)
  (let [tree (ensure-tree!)]
    (when (seq registries)
      (configurator/reconcile! tree host old-config config registries))
    {:config config :tree tree}))

(defn load-and-install!
  "Load config (loader) and install! it into the nexus. opts are loader opts
   plus :registries and :host (see install!). Returns
   {:config :errors :warnings :tree}."
  [{:keys [registries host] :as opts}]
  (let [{:keys [config errors warnings]} (config/load-config-result (dissoc opts :registries :host))
        {:keys [tree]}                   (install! {:config config :registries registries :host host})]
    {:config config :errors errors :warnings warnings :tree tree}))
