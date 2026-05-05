(ns isaac.comm.registry)

(def ^:dynamic *registry*
  (atom {:path  [:comms]
         :impls {}}))

(defn- ->name [x]
  (cond
    (string? x)  x
    (keyword? x) (name x)
    :else        (str x)))

(defn register-name!
  "Reserve an impl name in the registry. Idempotent — does not overwrite an
   already-registered factory."
  [impl-name]
  (let [n (->name impl-name)]
    (swap! *registry* update :impls
           (fn [impls] (update impls n #(or % :unbound))))
    n))

(defn register-factory!
  "Register a factory function for impl-name. Replaces any prior :unbound
   placeholder. Factory is (fn [host] -> Lifecycle)."
  [impl-name factory]
  (let [n (->name impl-name)]
    (swap! *registry* assoc-in [:impls n] factory)
    n))

(defn registered? [impl-name]
  (let [n (->name impl-name)]
    (contains? (:impls @*registry*) n)))

(defn factory-for [impl-name]
  (let [n       (->name impl-name)
        factory (get-in @*registry* [:impls n])]
    (when (and factory (not= :unbound factory))
      factory)))

(defn registered-names []
  (set (keys (:impls @*registry*))))

(defn fresh-registry
  "Returns a registry map suitable for binding *registry* in tests."
  ([] (fresh-registry [:comms]))
  ([path] {:path path :impls {}}))

(defn snapshot []
  @*registry*)
