(ns isaac.session.key
  (:require
    [clojure.string :as str]))

(defn build-key [{:keys [agent channel chatType conversation]}]
  (str "agent:" agent ":" channel ":" chatType ":" conversation))

(defn build-thread-key [parent-key thread-id]
  (str parent-key ":thread:" thread-id))

(defn parse-key
  ([key-str]
   (parse-key key-str {}))
  ([key-str {:keys [allow-short? include-crew?] :or {allow-short? false include-crew? false}}]
   (let [key-str (if (keyword? key-str) (name key-str) key-str)
         parts   (when (string? key-str) (str/split key-str #":"))
         result  (cond
                   (>= (count parts) 5)
                   {:agent        (nth parts 1)
                    :channel      (nth parts 2)
                    :chatType     (nth parts 3)
                    :conversation (nth parts 4)}

                   (and allow-short? (= (count parts) 3))
                   {:agent        (nth parts 1)
                    :channel      "cli"
                    :chatType     "direct"
                    :conversation (nth parts 2)}

                   :else nil)]
     (cond-> result
       (and include-crew? result) (assoc :crew (:agent result))))))
