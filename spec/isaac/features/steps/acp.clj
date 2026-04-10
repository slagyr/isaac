(ns isaac.features.steps.acp
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [cheshire.core :as json]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as acp-server]
    [isaac.features.matchers :as match]))

(def ^:private await-timeout-ms 1000)

(defn- parse-value [value]
  (cond
    (nil? value) nil
    (re-matches #"-?\d+" value) (parse-long value)
    (= "true" value) true
    (= "false" value) false
    (= "null" value) nil
    (and (or (str/starts-with? value "{") (str/starts-with? value "["))
         (or (str/ends-with? value "}") (str/ends-with? value "]")))
    (try
      (edn/read-string value)
      (catch Exception _ value))
    :else value))

(defn- ensure-vector-size [v idx]
  (let [v (if (vector? v) v [])]
    (if (< idx (count v))
      v
      (into v (repeat (inc (- idx (count v))) nil)))))

(defn- assoc-path* [data segments value]
  (if (empty? segments)
    value
    (let [[tag segment] (first segments)
          more          (rest segments)]
      (case tag
        :key
        (let [k     (keyword segment)
              m     (if (map? data) data {})
              child (get m k)]
          (assoc m k (assoc-path* child more value)))

        :idx
        (let [idx   segment
              v     (ensure-vector-size data idx)
              child (nth v idx)]
          (assoc v idx (assoc-path* child more value)))))))

(defn- assoc-path [message path value]
  (assoc-path* message (match/parse-path path) value))

(defn- table-rows [table]
  (map (fn [row] (zipmap (:headers table) row))
       (:rows table)))

(defn- table->message [table]
  (reduce (fn [message row]
            (assoc-path message
                        (get row "key")
                        (parse-value (get row "value"))))
          {}
          (table-rows table)))

(defn- enqueue-outgoing! [message]
  (when message
    (g/update! :acp-outgoing (fn [messages] (conj (vec (or messages [])) message)))))

(defn- record-dispatch-result! [result]
  (cond
    (nil? result)
    nil

    (and (map? result)
         (or (contains? result :response) (contains? result :notifications)))
    (do
      (enqueue-outgoing! (:response result))
      (doseq [notification (:notifications result)]
        (enqueue-outgoing! notification)))

    (sequential? result)
    (doseq [message result]
      (enqueue-outgoing! message))

    :else
    (enqueue-outgoing! result)))

(defn- dispatch-message! [message]
  (let [line        (json/generate-string message)
        dispatch-fn (or (g/get :acp-dispatch-fn)
                        (let [state-dir (g/get :state-dir)]
                          (when state-dir
                            (fn [input-line]
                              (acp-server/dispatch-line {:state-dir state-dir} input-line))))

                        (fn [input-line]
                          (rpc/handle-line (or (g/get :acp-handlers) {}) input-line)))]
    (record-dispatch-result! (dispatch-fn line))))

(defn- take-first-matching! [predicate]
  (let [found (atom nil)]
    (g/update! :acp-outgoing
               (fn [messages]
                 (let [messages (vec (or messages []))
                       idx      (first (keep-indexed (fn [i message]
                                                       (when (predicate message) i))
                                                     messages))]
                   (if (nil? idx)
                     messages
                     (do
                       (reset! found (nth messages idx))
                       (vec (concat (subvec messages 0 idx)
                                    (subvec messages (inc idx)))))))))
    @found))

(defn- await-message [predicate]
  (let [deadline (+ (System/currentTimeMillis) await-timeout-ms)]
    (loop []
      (if-let [message (take-first-matching! predicate)]
        message
        (if (< (System/currentTimeMillis) deadline)
          (do
            (Thread/sleep 10)
            (recur))
          nil)))))

(defwhen acp-client-sends-request "the ACP client sends request {id:int}:"
  [id table]
  (dispatch-message! (assoc (table->message table)
                            :jsonrpc "2.0"
                            :id id)))

(defwhen acp-client-sends-notification "the ACP client sends notification:"
  [table]
  (dispatch-message! (assoc (table->message table)
                            :jsonrpc "2.0")))

(defthen acp-agent-sends-response "the ACP agent sends response {id:int}:"
  [id table]
  (let [response (await-message #(= id (:id %)))]
    (g/should-not-be-nil response)
    (when response
      (let [result (match/match-object table response)]
        (g/should= [] (:failures result))))))

(defthen acp-agent-sends-notifications "the ACP agent sends notifications:"
  [table]
  (let [expected-count (count (:rows table))
        notifications  (loop [remaining expected-count
                              acc       []]
                         (if (zero? remaining)
                           acc
                           (if-let [notification (await-message #(and (contains? % :method)
                                                                      (not (contains? % :id))))]
                             (recur (dec remaining) (conj acc notification))
                             acc)))]
    (g/should= expected-count (count notifications))
    (let [result (match/match-entries table notifications)]
      (g/should= [] (:failures result)))))

(defgiven acp-client-initialized "the ACP client has initialized"
  []
  (dispatch-message! {:jsonrpc "2.0"
                      :id 0
                      :method "initialize"
                      :params {:protocolVersion 1}})
  (when-not (await-message #(= 0 (:id %)))
    (throw (ex-info "ACP initialize did not return a response" {:id 0}))))
