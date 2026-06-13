(ns isaac.comm.slots
  "The :isaac.server/comm config berth's slot factory. The reconciler
   asks it for an impl's constructor when a comm slot appears in user
   config: programmatic registrations (isaac.api/register-comm-factory!)
   win; otherwise the impl's entry in the module-index supplies the
   constructor, activating the owning module on first use."
  (:require
    [isaac.comm.registry :as comm-registry]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]))

(defn- ->name [x]
  (cond (keyword? x) (name x) :else (str x)))

(defn- activating-module-id [module-index impl-key]
  (some (fn [[module-id entry]]
          (when (get-in entry [:manifest :isaac.server/comm impl-key])
            module-id))
        module-index))

(defn- entry-factory [module-index module-id impl-key impl]
  (when-let [entry (get-in module-index [module-id :manifest :isaac.server/comm impl-key])]
    (try
      (some-> (:factory entry) requiring-resolve var-get)
      (catch Throwable t
        (log/error :module/activation-failed
                   :error  (.getMessage t)
                   :impl   (->name impl)
                   :module (name module-id))
        nil))))

(defn impl-factory
  "Returns the constructor (fn [host] -> Reconfigurable) for `impl`,
   or nil — logging :module/activation-failed — when none resolves."
  [host impl]
  (or (comm-registry/factory-for impl)
      (let [impl-key  (keyword (->name impl))
            module-id (activating-module-id (:module-index host) impl-key)]
        (if-not module-id
          (do (log/error :module/activation-failed
                         :error (str "no module contributes comm impl " (pr-str impl))
                         :impl  (->name impl))
              nil)
          (do
            (try
              (module-loader/activate! module-id (:module-index host))
              (catch clojure.lang.ExceptionInfo _ nil))
            (or (entry-factory (:module-index host) module-id impl-key impl)
                (do (log/error :module/activation-failed
                               :error  (str "module did not register comm impl " (pr-str impl))
                               :impl   (->name impl)
                               :module (name module-id))
                    nil)))))))

(defn- comm-config-decl [module-index]
  (some (fn [[_ entry]]
          (get-in entry [:manifest :berths :isaac.server/comm :config]))
        module-index))

(defn registry
  "The comm slot-tree registry for the reconciler, derived from the
   :isaac.server/comm berth declaration (falling back to the comm
   defaults when no declaration is in the index)."
  ([] (registry (module-loader/builtin-index)))
  ([module-index]
   (let [decl         (comm-config-decl module-index)
         decl-factory (some-> (get-in decl [:schema :value-spec :factory])
                              requiring-resolve
                              var-get)]
     {:kind         :slot-tree
      :berth-id     :isaac.server/comm
      :path         (or (:path decl) [:comms])
      :slot-factory (or decl-factory impl-factory)})))
