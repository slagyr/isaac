(ns isaac.session.key
  (:require
    [clojure.string :as str]))

(defn build-key [{:keys [agent channel chatType conversation]}]
  (str "agent:" agent ":" channel ":" chatType ":" conversation))

(defn build-thread-key [parent-key thread-id]
  (str parent-key ":thread:" thread-id))

(defn parse-key [key-str]
  (let [parts (str/split key-str #":")]
    (if (>= (count parts) 5)
      {:agent        (nth parts 1)
       :channel      (nth parts 2)
       :chatType     (nth parts 3)
       :conversation (nth parts 4)}
      nil)))
