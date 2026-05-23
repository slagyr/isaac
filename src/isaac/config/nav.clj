(ns isaac.config.nav
  "Schema-aware path walker and data mutators for Isaac config.

   Provides a walker that traverses dotted data-paths against a schema
   tree, returning the spec at the terminus or a structured error that
   names the failing segment. The schema drives interpretation: dynamic
   maps (those with :value-spec) absorb one segment as an entity-ID and
   advance to their value-spec; static maps look up the segment as a
   keyword field."
  (:require
    [clojure.string :as str]))

(defn- advance-spec [spec seg]
  (let [has-dynamic? (or (:value-spec spec) (:key-spec spec))]
    (if has-dynamic?
      (:value-spec spec)
      (when (map? (:schema spec))
        (get (:schema spec) (keyword seg))))))

(defn path->spec
  "Walk dotted path-str against root-schema using data-path semantics.
   Returns {:ok? true :spec <spec>} or {:ok? false :error <msg> :segment <seg>}."
  [root-schema path-str]
  (let [segments (str/split path-str #"\.")]
    (loop [spec      root-schema
           remaining segments]
      (if (empty? remaining)
        {:ok? true :spec spec}
        (let [seg       (first remaining)
              next-spec (advance-spec spec seg)]
          (if (nil? next-spec)
            {:ok? false
             :error   (str "unknown path: " path-str " (unrecognized segment: " seg ")")
             :segment seg}
            (recur next-spec (rest remaining))))))))

(defn- path-keys [path-str]
  (mapv keyword (str/split path-str #"\.")))

(defn set-value
  "Returns {:ok? true :config <new-config>} with value at path-str set,
   or {:ok? false :error <msg> :segment <seg>} if the path is unknown."
  [root-schema config path-str value]
  (let [result (path->spec root-schema path-str)]
    (if-not (:ok? result)
      (select-keys result [:ok? :error :segment])
      {:ok? true :config (assoc-in config (path-keys path-str) value)})))

(defn unset-value
  "Returns {:ok? true :config <new-config>} with path-str removed; idempotent.
   Always succeeds: absent paths are a no-op."
  [root-schema config path-str]
  (let [ks     (path-keys path-str)
        parent (vec (butlast ks))
        leaf   (last ks)]
    {:ok?    true
     :config (if (empty? parent)
               (dissoc config leaf)
               (update-in config parent dissoc leaf))}))
