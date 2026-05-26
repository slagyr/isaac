(ns isaac.config.install
  "Single coordinator for turning config into runtime state. Populates the
   nexus: ensures the session store, then reconciles the config-driven
   components (comms, hail, hooks, cron) directly into the nexus as live
   instances. Entry points call this instead of building config and installing
   components ad hoc.

   The registries to reconcile are injected by the caller so this namespace
   stays free of dependencies on the component implementations (comms, hail,
   hooks, cron)."
  (:require
    [isaac.config.loader :as config]
    [isaac.config.configurator :as configurator]
    [isaac.nexus :as nexus]
    [isaac.session.store :as store]))

(defn- ensure-store! [config]
  (when-not (store/registered-store)
    (when-let [state-dir (:state-dir config)]
      (store/register! config state-dir))))

(defn install!
  "Reconcile an already-committed config into the nexus: ensure the session store,
   then reconcile the given registries' config slices into the nexus as live
   component instances. The snapshot must already be committed (via load-config!
   or dangerously-install-config!) — install! no longer commits it.

   opts:
     :config      - the committed config map (required)
     :old-config  - previous config for the reconcile diff (nil on boot)
     :registries  - configurator registries to reconcile (default none)
     :host        - host context for reconcile! (module-index, connect-ws!, ...)
   Returns {:config config}."
  [{:keys [config old-config registries host]}]
  (ensure-store! config)
  (when (seq registries)
    (configurator/reconcile! host old-config config registries))
  {:config config})

(defn load-and-install!
  "Load config (loader), commit it as the snapshot, and install! it into the
   nexus. opts are loader opts plus :registries and :host (see install!).
   Returns {:config :errors :warnings}."
  [{:keys [registries host] :as opts}]
  (let [{:keys [config errors warnings]} (config/load-config-result (dissoc opts :registries :host))]
    (config/set-snapshot! config "load-and-install! coordinator")
    (install! {:config config :registries registries :host host})
    {:config config :errors errors :warnings warnings}))
