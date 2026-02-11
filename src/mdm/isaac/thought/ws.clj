(ns mdm.isaac.thought.ws
  "WebSocket handlers for thoughts."
  (:require [c3kit.wire.apic :as apic]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.thought.core :as thought]))

(def thought-types ["goal" "insight" "question" "share"])

(defn- all-thoughts
  "Get all thoughts of all types."
  []
  (mapcat thought/find-by-type thought-types))

(defn ws-recent
  "Returns recent thoughts with optional limit."
  [{:keys [params]}]
  (let [limit (get params :limit 20)
        thoughts (->> (all-thoughts)
                      (sort-by :id >)
                      (take limit))]
    (apic/ok (vec thoughts))))

(defn ws-search
  "Search thoughts by query text using embedding similarity."
  [{:keys [params]}]
  (let [{:keys [query limit]} params]
    (if (empty? query)
      (apic/fail)
      (let [query-embedding (embedding/text-embedding query)
            results (thought/find-similar query-embedding (or limit 10))]
        (apic/ok (vec results))))))
