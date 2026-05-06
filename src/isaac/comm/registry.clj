(ns isaac.comm.registry)

(def ^:dynamic *registry*
  (atom {:path  [:comms]
         :impls {}}))

(defn- ->name [x]
  (cond
    (string? x)  x
    (keyword? x) (name x)
    :else        (str x)))

(defn register-factory!
  "Register a factory function for impl-name. Factory is (fn [host] -> Lifecycle)."
  [impl-name factory]
  (let [n (->name impl-name)]
    (swap! *registry* assoc-in [:impls n] factory)
    n))

(defn registered? [impl-name]
  (let [n (->name impl-name)]
    (contains? (:impls @*registry*) n)))

(defn factory-for [impl-name]
  (get-in @*registry* [:impls (->name impl-name)]))

(defn registered-names []
  (set (keys (:impls @*registry*))))

(defn fresh-registry
  "Returns a registry map suitable for binding *registry* in tests."
  ([] (fresh-registry [:comms]))
  ([path] {:path path :impls {}}))

(defn snapshot []
  @*registry*)
