;; mutation-tested: 2026-05-06
(ns isaac.config.configurator
  (:require
    [clojure.string :as str]
    [isaac.comm.registry :as comm-registry]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.nexus :as nexus]))

(defprotocol Reconfigurable
  (on-startup!       [this slice])
  (on-config-change! [this old-slice new-slice]))

(defn ->name [x]
  (cond
    (keyword? x) (name x)
    :else        (str x)))

(defn- dotted [path]
  (str/join "." (map ->name path)))

(defn- activating-module-id [module-index impl]
  (let [impl-key (keyword (->name impl))]
    (some (fn [[module-id entry]]
            (when (get-in entry [:manifest :comm impl-key])
              module-id))
          module-index)))

(defn- resolve-factory [_registry host impl]
  (let [module-id (activating-module-id (:module-index host) impl)]
    (when module-id
      (try
        (module-loader/activate! module-id (:module-index host))
        (catch clojure.lang.ExceptionInfo _ nil)))
    (or (comm-registry/factory-for impl)
        (when module-id
          (log/error :module/activation-failed
                     :error  (str "module did not register comm impl " (pr-str impl))
                     :impl   (->name impl)
                     :module (name module-id))
          nil))))

(defn slot-impl
  "Resolves the comm type for a slot. Returns nil if slice is nil."
  [slot slice]
  (when slice
    (or (get slice :type)
        (get slice "type")
        (->name slot))))

(defn- slot-keys [container-cfg]
  (when (map? container-cfg)
    (set (keys container-cfg))))

(defn- singleton-impl [registry]
  (or (:impl registry)
      (->name (last (:path registry)))))

(defn- start-instance! [factory host slot-path slice impl]
  (let [host-with-name (assoc host :name (last slot-path))
        instance-name  (->name (last slot-path))
        instance       (factory host-with-name)]
    (on-startup! instance slice)
    (when (= [:comms] (:path (:registry host)))
      (comm-registry/register-instance! impl instance))
    (nexus/register! slot-path instance)
    (log/info :lifecycle/started :path (dotted slot-path) :impl impl)
    (when (= [:comms] (:path (:registry host)))
      (log/info :comm/activated :comm instance-name :type impl))
    instance))

(defn- stop-instance! [instance slot-path old-slice impl]
  (on-config-change! instance old-slice nil)
  (when (= [:comms] (butlast slot-path))
    (comm-registry/deregister-instance! impl))
  (nexus/deregister! slot-path)
  (log/info :lifecycle/stopped :path (dotted slot-path) :impl impl))

(defn- change-instance! [instance slot-path old-slice new-slice impl]
  (on-config-change! instance old-slice new-slice)
  (log/info :lifecycle/changed :path (dotted slot-path) :impl impl))

(defn- reconcile-slot! [host registry slot-path old-slice new-slice]
  (let [slot     (last slot-path)
        old-impl (slot-impl slot old-slice)
        new-impl (slot-impl slot new-slice)
        existing (nexus/get-in slot-path)]
    (cond
      (and (nil? old-slice) (some? new-slice))
      (when-let [factory (resolve-factory registry host new-impl)]
        (start-instance! factory host slot-path new-slice new-impl))

      (and (some? old-slice) (nil? new-slice))
      (when existing
        (stop-instance! existing slot-path old-slice old-impl))

      (not= old-impl new-impl)
      (do
        (when existing
          (stop-instance! existing slot-path old-slice old-impl))
        (when-let [factory (resolve-factory registry host new-impl)]
          (start-instance! factory host slot-path new-slice new-impl)))

      (not= old-slice new-slice)
      (when existing
        (change-instance! existing slot-path old-slice new-slice new-impl)))))

(defn- reconcile-component! [host old-cfg new-cfg registry]
  (let [path      (vec (:path registry))
        old-slice (get-in old-cfg path)
        new-slice (get-in new-cfg path)
        existing  (nexus/get-in path)
        factory   (:factory registry)
        impl      (singleton-impl registry)
        host      (assoc host :registry registry)]
    (cond
      (and (nil? old-slice) (some? new-slice) (nil? existing))
      (start-instance! factory host path new-slice impl)

      (and (some? old-slice) (nil? new-slice) existing)
      (stop-instance! existing path old-slice impl)

      (and (not= old-slice new-slice) existing)
      (change-instance! existing path old-slice new-slice impl)

      (and (not= old-slice new-slice) (some? new-slice) (nil? existing))
      (start-instance! factory host path new-slice impl))))

(defn- reconcile-registry! [host old-cfg new-cfg registry]
  (case (:kind registry)
    :component (reconcile-component! host old-cfg new-cfg registry)
    (let [path        (:path registry)
          old-cont    (get-in old-cfg path)
          new-cont    (get-in new-cfg path)
          slot-names  (into (or (slot-keys old-cont) #{})
                            (or (slot-keys new-cont) #{}))
          host        (assoc host :registry registry)]
      (doseq [slot slot-names]
        (let [old-slice (get old-cont slot)
              new-slice (get new-cont slot)
              slot-path (conj (vec path) slot)]
          (reconcile-slot! host registry slot-path old-slice new-slice))))))

(defn reconcile!
  "Walks user-chosen slots under (:path registry), reconciling the live
   component instances in the nexus against config-tree slices. One function
   for boot, reload, and shutdown.

   Boot:    (reconcile! host nil  cfg  registry)
   Reload:  (reconcile! host old  new  registry)
   Stop:    (reconcile! host @cfg nil  registry)"
  [host old-cfg new-cfg registry-or-registries]
  (if (map? registry-or-registries)
    (reconcile-registry! host old-cfg new-cfg registry-or-registries)
    (doseq [registry registry-or-registries]
      (reconcile-registry! host old-cfg new-cfg registry))))
