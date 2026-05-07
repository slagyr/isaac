;; mutation-tested: 2026-05-06
(ns isaac.configurator
  (:require
    [clojure.string :as str]
    [isaac.comm.registry :as comm-registry]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]))

(defprotocol Reconfigurable
  (on-startup!       [this slice])
  (on-config-change! [this old-slice new-slice]))

(defn- ->name [x]
  (cond
    (keyword? x) (name x)
    :else        (str x)))

(defn- dotted [path]
  (str/join "." (map (fn [p] (cond
                                (keyword? p) (name p)
                                :else        (str p)))
                     path)))

(defn- activating-module-id [module-index impl]
  (let [impl-key (keyword (->name impl))]
    (some (fn [[module-id entry]]
            (when (get-in entry [:manifest :extends :comm impl-key])
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
  "Resolves the :impl for a slot. Explicit :impl wins; otherwise the slot name
   is used as a shorthand. Returns nil if slice is nil."
  [slot slice]
  (when slice
    (or (get slice :impl)
        (get slice "impl")
        (->name slot))))

(defn- assoc-tree [tree path value]
  (if (empty? path)
    value
    (assoc-in tree path value)))

(defn- dissoc-tree [tree path]
  (cond
    (empty? path) nil
    (= 1 (count path)) (dissoc tree (first path))
    :else (let [parent-path (vec (butlast path))
                leaf        (last path)
                parent      (get-in tree parent-path)]
            (if (map? parent)
              (assoc-in tree parent-path (dissoc parent leaf))
              tree))))

(defn- slot-keys [container-cfg]
  (when (map? container-cfg)
    (set (keys container-cfg))))

(defn- start-instance! [tree-atom factory host slot-path slice impl]
  (let [host-with-name (assoc host :name (last slot-path))
        instance       (factory host-with-name)]
    (on-startup! instance slice)
    (comm-registry/register-instance! impl instance)
    (swap! tree-atom assoc-tree slot-path instance)
    (log/info :lifecycle/started :path (dotted slot-path) :impl impl)
    instance))

(defn- stop-instance! [tree-atom instance slot-path old-slice impl]
  (on-config-change! instance old-slice nil)
  (comm-registry/deregister-instance! impl)
  (swap! tree-atom dissoc-tree slot-path)
  (log/info :lifecycle/stopped :path (dotted slot-path) :impl impl))

(defn- change-instance! [instance slot-path old-slice new-slice impl]
  (on-config-change! instance old-slice new-slice)
  (log/info :lifecycle/changed :path (dotted slot-path) :impl impl))

(defn- reconcile-slot! [tree-atom host registry slot-path old-slice new-slice]
  (let [slot     (last slot-path)
        old-impl (slot-impl slot old-slice)
        new-impl (slot-impl slot new-slice)
        existing (get-in @tree-atom slot-path)]
    (cond
      (and (nil? old-slice) (some? new-slice))
      (when-let [factory (resolve-factory registry host new-impl)]
        (start-instance! tree-atom factory host slot-path new-slice new-impl))

      (and (some? old-slice) (nil? new-slice))
      (when existing
        (stop-instance! tree-atom existing slot-path old-slice old-impl))

      (not= old-impl new-impl)
      (do
        (when existing
          (stop-instance! tree-atom existing slot-path old-slice old-impl))
        (when-let [factory (resolve-factory registry host new-impl)]
          (start-instance! tree-atom factory host slot-path new-slice new-impl)))

      (not= old-slice new-slice)
      (when existing
        (change-instance! existing slot-path old-slice new-slice new-impl)))))

(defn reconcile!
  "Walks user-chosen slots under (:path registry), reconciling object-tree
   instances against config-tree slices. One function for boot, reload, and
   shutdown.

   Boot:    (reconcile! tree host nil  cfg  registry)
   Reload:  (reconcile! tree host old  new  registry)
   Stop:    (reconcile! tree host @cfg nil  registry)"
  [tree-atom host old-cfg new-cfg registry]
  (let [path        (:path registry)
        old-cont    (get-in old-cfg path)
        new-cont    (get-in new-cfg path)
        slot-names  (into (or (slot-keys old-cont) #{})
                          (or (slot-keys new-cont) #{}))]
    (doseq [slot slot-names]
      (let [old-slice (get old-cont slot)
            new-slice (get new-cont slot)
            slot-path (conj (vec path) slot)]
        (reconcile-slot! tree-atom host registry slot-path old-slice new-slice)))))
