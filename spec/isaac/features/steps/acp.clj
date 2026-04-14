(ns isaac.features.steps.acp
  (:import
    (java.io StringWriter)
    (java.util.concurrent LinkedBlockingQueue TimeUnit))
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [cheshire.core :as json]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as acp-server]
    [isaac.acp.ws :as ws]
    [isaac.features.matchers :as match]
    [isaac.main :as main]
    [isaac.session.storage :as storage]
    [isaac.server.acp-websocket :as acp-websocket]
    [ring.util.codec :as codec]))

(defn- query-params [query-string]
  (codec/form-decode (or query-string "")))

(def ^:private await-timeout-ms 3000)

(defn- close-loopback! []
  (when-let [client (g/get :acp-loopback-client)]
    (ws/ws-close! client))
  (when-let [server (g/get :acp-loopback-server)]
    (ws/ws-close! server))
  (when-let [^LinkedBlockingQueue queue (g/get :proxy-stdin-queue)]
    (.put queue :closed))
  (when-let [runner (g/get :acp-proxy-runner)]
    (future-cancel runner))
  (when-let [server-runner (g/get :acp-loopback-server-runner)]
    (future-cancel server-runner)))

(g/after-scenario close-loopback!)

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

(defn- outgoing-queue ^LinkedBlockingQueue []
  (or (g/get :acp-outgoing)
      (let [q (LinkedBlockingQueue.)]
        (g/assoc! :acp-outgoing q)
        q)))

(defn- enqueue-outgoing! [message]
  (when message
    (.put (outgoing-queue) message)))

(defn- enqueue-output-lines! [writer]
  (doseq [line (->> (str/split-lines (str writer))
                    (remove str/blank?))]
    (enqueue-outgoing! (json/parse-string line true))))

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
  (let [line          (json/generate-string message)
        state-dir     (g/get :state-dir)
        custom-fn     (g/get :acp-dispatch-fn)
        fallback-fn   (fn [input-line]
                        (rpc/handle-line (or (g/get :acp-handlers) {}) input-line))
        run-dispatch! (fn []
                        (cond
                          custom-fn
                          (record-dispatch-result! (custom-fn line))

                          state-dir
                          (let [writer (java.io.StringWriter.)
                                result (acp-server/dispatch-line {:state-dir       state-dir
                                                                  :agents          (g/get :agents)
                                                                  :models          (g/get :models)
                                                                  :provider-configs (g/get :provider-configs)
                                                                  :output-writer   writer}
                                                                 line)]
                            (enqueue-output-lines! writer)
                            (record-dispatch-result! result))

                          :else
                          (record-dispatch-result! (fallback-fn line))))]
    (if (= "session/prompt" (:method message))
      (future
        (Thread/sleep 20)
        (run-dispatch!))
      (run-dispatch!))))

(defn- await-message [predicate]
  (let [queue    (outgoing-queue)
        deadline (+ (System/currentTimeMillis) await-timeout-ms)
        skipped  (java.util.ArrayList.)]
    (try
      (loop []
        (let [remaining (- deadline (System/currentTimeMillis))]
          (if (<= remaining 0)
            nil
            (if-let [message (.poll queue remaining TimeUnit/MILLISECONDS)]
              (if (predicate message)
                message
                (do (.add skipped message)
                    (recur)))
              nil))))
      (finally
        (doseq [m skipped]
          (.put queue m))))))

(defn- output-messages []
  (let [output (if-let [writer (g/get :live-output-writer)] (str writer) (g/get :output))]
    (->> (str/split-lines (or output ""))
       (remove str/blank?)
       (mapv #(json/parse-string % true)))))

(defn- await-output-response [id]
  (let [deadline (+ (System/currentTimeMillis) await-timeout-ms)]
    (loop []
      (if-let [response (first (filter #(= id (:id %)) (output-messages)))]
        response
        (if (< (System/currentTimeMillis) deadline)
          (do
            (Thread/sleep 1)
            (recur))
          nil)))))

(defn- loopback-request []
  (or (g/get :acp-loopback-request) {}))

(defn- loopback-server-opts [state-dir agents models provider-cfgs]
  (let [request     (loopback-request)
        query       (query-params (:query-string request))
        resume?     (= "true" (get query "resume"))
        agent-id    (or (get query "crew") (get query "agent") "main")
        resumed-key (when resume?
                      (some->> (storage/list-sessions state-dir agent-id)
                               (sort-by :updatedAt)
                               last
                               :key))]
    {:request     {:headers      {"x-forwarded-for" "loopback"}
                   :query-string (:query-string request)
                   :uri          "/acp"}
     :resumed-key resumed-key
     :server-opts {:state-dir        state-dir
                   :agents           agents
                   :models           models
                   :provider-configs provider-cfgs
                   :agent-id         agent-id
                   :model-override   (get query "model")}}))

(defn- loopback-result [state-dir agents models provider-cfgs writer line]
  (let [{:keys [request resumed-key server-opts]} (loopback-server-opts state-dir agents models provider-cfgs)
        server-opts (assoc server-opts :output-writer writer)]
    (if resumed-key
      (let [handlers (assoc (acp-server/handlers server-opts)
                       "session/new" (fn [_ _] {:sessionId resumed-key}))]
        (rpc/handle-line handlers line))
      (acp-websocket/dispatch-line server-opts request line))))

(defn- emit-loopback-result! [server-conn result]
  (when result
    (cond
      (contains? result :notifications)
      (do
        (doseq [notification (:notifications result)]
          (ws/ws-send! server-conn (json/generate-string notification)))
        (when-let [response (:response result)]
          (ws/ws-send! server-conn (json/generate-string response))))

      (contains? result :response)
      (ws/ws-send! server-conn (json/generate-string (:response result)))

      :else
      (ws/ws-send! server-conn (json/generate-string result)))))

(defn- await-release! []
  (when-let [release* (g/get :loopback-final-response-release)]
    @release*))

(defn- emit-final-response! [server-conn result]
  (await-release!)
  (emit-loopback-result! server-conn result))

(defn- hold-final-response? [line result]
  (and (g/get :loopback-hold-final-response?)
       (= "session/prompt" (:method (json/parse-string line true)))
       (or (contains? result :response)
           (contains? result :result))))

(defn- serve-loopback-connection! [server-conn state-dir agents models provider-cfgs]
  (loop []
    (when-let [line (ws/ws-receive! server-conn)]
      (let [writer (StringWriter.)
            result (loopback-result state-dir agents models provider-cfgs writer line)]
        (doseq [message-line (ws/written-lines writer)]
          (ws/ws-send! server-conn message-line))
        (if (hold-final-response? line result)
          (emit-final-response! server-conn result)
          (emit-loopback-result! server-conn result)))
      (recur))))

(defn- start-loopback-server! [transport state-dir agents models provider-cfgs]
  (future
    (loop []
      (if-let [server-conn (ws/accept-loopback! transport)]
        (do
          (serve-loopback-connection! server-conn state-dir agents models provider-cfgs)
          (when-not @(:permanent? transport)
            (recur)))
        (when-not @(:permanent? transport)
          (recur))))))

(defn ensure-loopback-proxy! []
  (let [transport      (or (g/get :acp-reconnectable-loopback)
                           (let [t (ws/reconnectable-loopback)]
                             (g/assoc! :acp-reconnectable-loopback t)
                             t))
        state-dir      (g/get :state-dir)
        agents         (g/get :agents)
        models         (g/get :models)
        provider-cfgs  (g/get :provider-configs)]
    (when-not (g/get :acp-loopback-server-runner)
      (g/assoc! :acp-loopback-server-runner (start-loopback-server! transport state-dir agents models provider-cfgs)))
    (g/assoc! :acp-remote-connection-factory
              (fn [url _]
                (g/assoc! :acp-loopback-request {:query-string (when (str/includes? url "?")
                                                                 (subs url (inc (str/index-of url "?"))))})
                (ws/connect-loopback! transport url)))))

(defn- parse-argv [args]
  (if (str/blank? args)
    []
    (loop [s (str/trim args) tokens []]
      (if (str/blank? s)
        tokens
        (cond
          (str/starts-with? s "'")
          (let [end (str/index-of s "'" 1)]
            (if end
              (recur (str/trim (subs s (inc end))) (conj tokens (subs s 1 end)))
              (conj tokens (subs s 1))))

          (str/starts-with? s "\"")
          (let [end (str/index-of s "\"" 1)]
            (if end
              (recur (str/trim (subs s (inc end))) (conj tokens (subs s 1 end)))
              (conj tokens (subs s 1))))

          :else
          (let [[tok rest-s] (str/split s #"\s+" 2)]
            (recur (or rest-s "") (conj tokens tok))))))))

(defn- next-proxy-line []
  (let [^LinkedBlockingQueue queue (g/get :proxy-stdin-queue)]
    (when-let [line (.poll queue 5 TimeUnit/SECONDS)]
      (when-not (= :closed line)
        line))))

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
  (let [expected-count   (count (:rows table))
        notification?    #(and (contains? % :method) (not (contains? % :id)))
        matching-window  (fn [notifications]
                           (let [notifications (vec notifications)]
                             (some (fn [start]
                                     (let [candidate (subvec notifications start (+ start expected-count))
                                           result    (match/match-entries table candidate)]
                                       (when (= [] (:failures result)) candidate)))
                                   (range 0 (inc (- (count notifications) expected-count))))))
        deadline         (+ (System/currentTimeMillis) await-timeout-ms)]
    (loop [notifications []]
      (if-let [candidate (when (<= expected-count (count notifications))
                           (matching-window notifications))]
        (g/should= expected-count (count candidate))
        (let [remaining (- deadline (System/currentTimeMillis))]
          (if (<= remaining 0)
            (g/should= [] (:failures (match/match-entries table (take expected-count notifications))))
            (if-let [notification (await-message notification?)]
              (recur (conj notifications notification))
              (g/should= [] (:failures (match/match-entries table (take expected-count notifications)))))))))))

(defgiven acp-proxy-connected-loopback "the ACP proxy is connected via loopback"
  []
  (let [{:keys [client server]} (ws/loopback-pair)
        state-dir               (g/get :state-dir)
        agents                  (or (g/get :agents) {})
        models                  (or (g/get :models) {})
        provider-configs        (or (g/get :provider-configs) {})]
    (future
      (loop []
        (when-let [line (ws/ws-receive! server)]
          (let [writer      (java.io.StringWriter.)
                request     (or (g/get :acp-loopback-request) {})
                 query       (query-params (:query-string request))
                 resume?     (= "true" (get query "resume"))
                 agent-id    (or (get query "crew") (get query "agent") "main")
                 resumed-key (when resume?
                               (some->> (storage/list-sessions state-dir agent-id)
                                        (sort-by :updatedAt)
                                        last
                                        :key))
                server-opts {:state-dir        state-dir
                             :agents           agents
                             :models           models
                             :provider-configs provider-configs
                             :output-writer    writer
                             :agent-id         agent-id
                             :model-override   (get query "model")}
                ws-request   {:headers      {"x-forwarded-for" "loopback"}
                              :query-string (:query-string request)
                              :uri          "/acp"}
                result       (if resumed-key
                               (let [handlers (assoc (acp-server/handlers server-opts)
                                                "session/new" (fn [_ _] {:sessionId resumed-key}))]
                                 (rpc/handle-line handlers line))
                               (acp-websocket/dispatch-line server-opts ws-request line))]
            (doseq [message-line (ws/written-lines writer)]
              (ws/ws-send! server message-line))
            (when result
              (cond
                (contains? result :notifications)
                (do
                  (doseq [notification (:notifications result)]
                    (ws/ws-send! server (json/generate-string notification)))
                  (when-let [response (:response result)]
                    (ws/ws-send! server (json/generate-string response))))

                (contains? result :response)
                (ws/ws-send! server (json/generate-string (:response result)))

                :else
                (ws/ws-send! server (json/generate-string result)))))
          (recur))))
    (g/assoc! :acp-loopback-client client)
    (g/assoc! :acp-loopback-server server)
    (g/assoc! :acp-remote-connection-factory (fn [url _]
                                               (g/assoc! :acp-loopback-request {:query-string (when (str/includes? url "?")
                                                                                                (subs url (inc (str/index-of url "?"))))})
                                               client))))

(defgiven acp-proxy-running "the acp proxy is running with {args:string}"
  [args]
  (let [transport      (or (g/get :acp-reconnectable-loopback)
                           (let [t (ws/reconnectable-loopback)]
                             (g/assoc! :acp-reconnectable-loopback t)
                             t))
        stdin-queue    (LinkedBlockingQueue.)
        output-writer  (StringWriter.)
        error-writer   (StringWriter.)
        argv           (parse-argv args)
        state-dir      (g/get :state-dir)
        agents         (g/get :agents)
        models         (g/get :models)
        provider-cfgs  (g/get :provider-configs)
        cfg            (or (g/get :server-config) {})
        server-runner* (start-loopback-server! transport state-dir agents models provider-cfgs)
        run*           (future
                         (binding [*in*  (java.io.BufferedReader. (java.io.StringReader. ""))
                                   *out* output-writer
                                   *err* error-writer
                                   main/*extra-opts* {:state-dir state-dir
                                                      :agents    agents
                                                      :models    models
                                                      :provider-configs provider-cfgs
                                                      :acp-proxy-max-reconnects (get-in cfg [:acp :proxy-max-reconnects])
                                                      :acp-proxy-reconnect-delay-ms (get-in cfg [:acp :proxy-reconnect-delay-ms])
                                                      :acp-read-line-fn next-proxy-line
                                                      :ws-connection-factory (fn [url _]
                                                                               (g/assoc! :acp-loopback-request {:query-string (when (str/includes? url "?")
                                                                                                                        (subs url (inc (str/index-of url "?"))))})
                                                                               (ws/connect-loopback! transport url))}]
                           (g/assoc! :exit-code (main/run argv))))]
    (g/assoc! :acp-loopback-server-runner server-runner*)
    (g/assoc! :proxy-stdin-queue stdin-queue)
    (g/assoc! :live-output-writer output-writer)
    (g/assoc! :live-error-writer error-writer)
    (g/assoc! :acp-proxy-runner run*)))

(defwhen stdin-receives "stdin receives:"
  [content]
  (let [lines (str/split-lines (if (str/ends-with? content "\n") content (str content "\n")))
        ^LinkedBlockingQueue queue (g/get :proxy-stdin-queue)]
    (doseq [line lines]
      (.put queue line))))

(defwhen loopback-drops "the loopback connection drops"
  []
  (Thread/sleep 100)
  (ws/drop-loopback! (g/get :acp-reconnectable-loopback)))

(defwhen loopback-restored "the loopback connection is restored"
  []
  (ws/restore-loopback! (g/get :acp-reconnectable-loopback)))

(defwhen loopback-drops-permanently "the loopback connection drops permanently"
  []
  (Thread/sleep 100)
  (ws/drop-loopback-permanently! (g/get :acp-reconnectable-loopback)))

(defgiven loopback-holds-final-response "the loopback holds the final response"
  []
  (g/assoc! :loopback-hold-final-response? true)
  (g/assoc! :loopback-final-response-release (promise)))

(defwhen loopback-releases-final-response "the loopback releases the final response"
  []
  (g/assoc! :loopback-hold-final-response? false)
  (when-let [release* (g/get :loopback-final-response-release)]
    (deliver release* :ok)))

(defthen output-contains-json-rpc-response "the output has a JSON-RPC response for id {id:int}:"
  [id table]
  (let [response (await-output-response id)]
    (g/should-not-be-nil response)
    (when response
      (let [result (match/match-object table response)]
        (g/should= [] (:failures result))))))

(defgiven acp-client-initialized "the ACP client has initialized"
  []
  (dispatch-message! {:jsonrpc "2.0"
                      :id 0
                      :method "initialize"
                      :params {:protocolVersion 1}})
  (when-not (await-message #(= 0 (:id %)))
    (throw (ex-info "ACP initialize did not return a response" {:id 0}))))
