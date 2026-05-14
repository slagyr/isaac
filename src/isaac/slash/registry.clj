(ns isaac.slash.registry
  (:require
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]))

(defonce ^:private commands* (atom {}))

(defn- ensure-builtins! []
  ((requiring-resolve 'isaac.slash.builtin/ensure-registered!)))

(defn registered-command [name]
  (get @commands* (str name)))

(defn register! [{:keys [name] :as command}]
  (let [name     (str name)
        previous (get @commands* name)]
    (swap! commands* assoc name (assoc command :name name))
    (if previous
      (log/warn :slash/override :command name)
      (log/info :slash/registered :command name :module name))
    name))

(defn unregister! [name]
  (swap! commands* dissoc (str name)))

(defn clear! []
  (reset! commands* {}))

(defn- activate-all! [module-index]
  (doseq [[module-id entry] module-index
          :when (seq (get-in entry [:manifest :slash-commands]))]
    (module-loader/activate! module-id module-index)))

(defn lookup
  ([name]
   (ensure-builtins!)
   (registered-command name))
  ([name module-index]
   (ensure-builtins!)
   (activate-all! module-index)
   (registered-command name)))

(defn all-commands
  ([]
   (ensure-builtins!)
   (->> (vals @commands*)
        (sort-by :name)
        (map #(dissoc % :handler))
        vec))
  ([module-index]
    (ensure-builtins!)
   (activate-all! module-index)
   (->> (vals @commands*)
        (sort-by :name)
        (map #(dissoc % :handler))
        vec)))
