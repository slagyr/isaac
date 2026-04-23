(ns isaac.cli.acp
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.comm.acp :as acp]
    [clojure.tools.cli :as tools-cli]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as server]
    [isaac.acp.ws :as ws]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(def option-spec
  [["-v" "--verbose"      "Log inbound method names to stderr"]
   ["-s" "--session KEY"  "Attach to an existing session key"]
   ["-m" "--model ALIAS"  "Override the agent's default model"]
   ["-c" "--crew NAME"    "Use a named crew member (default: main)"]
   ["-R" "--resume"       "Resume the most recent session for the crew member"]
   ["-r" "--remote URL"   "Proxy ACP over a remote WebSocket endpoint"]
   ["-t" "--token TOKEN"  "Bearer token for remote ACP authentication"]
   ["-h" "--help"         "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options
                   (remove (comp nil? val))
                   (into {}))
     :errors  errors}))

(defn- home-dir [{:keys [home state-dir]}]
  (or home state-dir (System/getProperty "user.home")))

(defn- valid-model? [server-opts model-alias]
  (if-let [models (:models server-opts)]
    (contains? models model-alias)
    (let [cfg          (:cfg server-opts)
          named-models (:models (config/normalize-config cfg))]
      (boolean (or (get named-models model-alias)
                    (config/parse-model-ref model-alias))))))

(defn- build-server-opts [opts]
  (let [home      (home-dir opts)
        cfg       (config/normalize-config (config/load-config {:home home}))
        sdir      (or (:state-dir opts) (:stateDir cfg)
                       (str home "/.isaac"))
        out       (or (:output-writer opts) *out*)
        agents    (or (when (map? (:crew opts)) (:crew opts)) (:agents opts))
        models    (:models opts)
        prov-cfgs (:provider-configs opts)
        agent-id  (or (when (string? (:crew opts)) (:crew opts)) (:agent opts))]
    (cond-> {:state-dir sdir :home home :output-writer out}
      agents    (assoc :agents agents)
      models    (assoc :models models)
      prov-cfgs (assoc :provider-configs prov-cfgs)
      agent-id  (assoc :agent-id agent-id)
      (nil? agents) (assoc :cfg cfg))))

(defn- write-result! [result]
  (when result
    (cond
      (contains? result :notifications)
      (do (doseq [n (:notifications result)]
            (rpc/write-message! *out* n))
          (when-let [r (:response result)]
            (rpc/write-message! *out* r)))

      (contains? result :response)
      (rpc/write-message! *out* (:response result))

      :else
      (rpc/write-message! *out* result))))

(defn- session-exists? [state-dir session-key]
  (some? (storage/get-transcript state-dir session-key)))

(defn- find-most-recent-session [state-dir agent-id]
  (->> (storage/list-sessions state-dir agent-id)
       (sort-by :updatedAt)
       last))

(defn- resumed-session-key [state-dir agent-id]
  (some-> (find-most-recent-session state-dir agent-id) :id))

(defn- attach-session-handler [handlers session-key]
  (assoc handlers "session/new" (fn [_ _] {:sessionId session-key})))

(defn- run-loop [handlers]
  (let [reader (java.io.BufferedReader. *in*)]
    (loop []
      (when-let [line (.readLine reader)]
        (write-result! (rpc/handle-line handlers line))
        (recur)))))

(defn- run-loop-verbose [handlers]
  (let [dispatch* rpc/dispatch]
    (with-redefs [rpc/dispatch (fn [dispatch-handlers message]
                                 (when-let [method (:method message)]
                                   (binding [*out* *err*]
                                     (println method)))
                                 (dispatch* dispatch-handlers message))]
      (run-loop handlers))))

(defn- print-error! [message]
  (binding [*out* *err*]
    (println message)))

(defn- ensure-local-config! [opts]
  (when-not (or (map? (:crew opts))
                (map? (:agents opts)))
    (let [result (config/load-config-result {:home (home-dir opts)})]
      (when (:missing-config? result)
        (print-error! (get-in result [:errors 0 :value]))
        false))))

(defn- write-line! [line]
  (.write *out* line)
  (.write *out* "\n")
  (.flush *out*))

(defn- write-notification! [message]
  (.write *out* (jrpc/notification-line "session/update" {:message message}))
  (.flush *out*))

(defn- parse-line [line]
  (try
    (json/parse-string line true)
    (catch Exception _ nil)))

(defn- message-session-id [message]
  (or (get-in message [:params :sessionId])
      (get-in message [:result :sessionId])))

(defn- cache-session-id! [session-id* line]
  (when-let [session-id (some-> line parse-line message-session-id)]
    (reset! session-id* session-id)))

(defn- agent-id [{:keys [agent crew]}]
  (or (when (string? crew) crew) agent "main"))

(defn- default-session-id [opts]
  (or (:session opts)
      (some-> (find-most-recent-session (:state-dir opts) (agent-id opts)) :id)))

(defn- status-notification [session-id text]
  (assoc-in (acp/text-update session-id text) [:params :update :sessionUpdate] "agent_thought_chunk"))

(defn- write-status-notification! [session-id* opts text]
  (when-let [session-id (or @session-id* (default-session-id opts))]
    (reset! session-id* session-id)
    (rpc/write-message! *out* (status-notification session-id text))))

(defn- write-remote-connection-error! [id]
  (when id
    (rpc/write-message! *out* {:jsonrpc "2.0"
                               :id      id
                               :error   {:code    -32099
                                         :message "remote connection lost, reconnecting"}})))

(defn- request-id [line]
  (try
    (:id (json/parse-string line true))
    (catch Exception _ nil)))

(defn- proxy-event-name [method]
  ({"initialize"     :acp-proxy/initialize
    "session/new"    :acp-proxy/session-new
    "session/prompt" :acp-proxy/session-prompt} method))

(defn- log-proxy-message! [url line]
  (let [message    (json/parse-string line true)
        event      (proxy-event-name (:method message))
        session-id (get-in message [:params :sessionId])]
    (when event
      (log/debug event
                 :sessionId session-id
                 :url       url))))

(defn- authentication-error? [error]
  (let [cause      (or (ex-cause error) error)
        class-name (.getName (class cause))
        message    (or (.getMessage cause) "")]
    (or (= "java.net.http.WebSocketHandshakeException" class-name)
        (re-find #"(?i)401|unauthorized|authentication failed" message))))

(defn- remote-headers [token]
  (cond-> {}
    token (assoc "Authorization" (str "Bearer " token))))

(defn- url-encode [value]
  (java.net.URLEncoder/encode (str value) "UTF-8"))

(defn- remote-query-params [opts]
  (cond-> []
    (:model opts)  (conj ["model" (:model opts)])
    (or (when (string? (:crew opts)) (:crew opts)) (:agent opts)) (conj ["crew" (or (when (string? (:crew opts)) (:crew opts)) (:agent opts))])
    (:resume opts) (conj ["resume" "true"])))

(defn- remote-url [opts]
  (let [base   (:remote opts)
        params (remote-query-params opts)]
    (if (empty? params)
      base
      (str base
           (if (str/includes? base "?") "&" "?")
           (str/join "&" (map (fn [[k v]] (str k "=" (url-encode v))) params))))))

(defn- connect-remote! [factory url token]
  (factory url {:headers (remote-headers token)}))

(defn- start-input-reader! [opts]
  (let [reader       (java.io.BufferedReader. *in*)
        read-line-fn (or (:acp-read-line-fn opts) #(.readLine reader))
        queue        (java.util.concurrent.LinkedBlockingQueue.)]
    (future
      (loop []
        (if-let [line (read-line-fn)]
          (do
            (.put queue {:type :stdin :line line})
            (recur))
          (.put queue {:type :stdin-closed}))))
    queue))

(defn- start-remote-reader! [conn]
  (let [queue (java.util.concurrent.LinkedBlockingQueue.)]
    (future
      (loop []
        (let [message-line (ws/ws-receive! conn)]
          (cond
            (nil? message-line)
            (.put queue {:type :connection-lost})

            (:error message-line)
            (.put queue {:type :connection-error :error (:error message-line)})

            :else
            (do
              (.put queue {:type :message :line message-line})
              (recur))))))
    queue))

(defn- reconnect-delay-ms [attempt opts]
  (let [base-delay (or (:acp-proxy-reconnect-delay-ms opts) 1000)
        max-delay  (or (:acp-proxy-reconnect-max-delay-ms opts) 5000)]
    (min max-delay (* base-delay (long (Math/pow 2 (dec attempt)))))))

(defn- reconnect! [active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts]
  (when (compare-and-set! reconnecting? nil ::starting)
    (let [runner (future
                   (try
                     (loop [attempt 1]
                       (when @active?
                         (log/info :acp-proxy/reconnect-attempt :attempt attempt :url url)
                         (Thread/sleep (reconnect-delay-ms attempt opts))
                         (if-let [new-conn (try
                                             (connect-remote! factory url token)
                                             (catch Exception _ nil))]
                           (do
                             (reset! conn* new-conn)
                             (reset! remote-queue* (start-remote-reader! new-conn))
                             (reset! disconnected? false)
                             (write-status-notification! session-id* opts "reconnected to remote")
                             (log/debug :acp-proxy/connected :url url))
                           (recur (inc attempt)))))
                     (catch InterruptedException _
                       nil)
                     (finally
                       (reset! reconnecting? nil))))]
      (reset! reconnecting? runner)))
  nil)

(defn- connection-lost! [active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts]
  (when-not @disconnected?
    (reset! disconnected? true)
    (write-status-notification! session-id* opts "remote connection lost")
    (log/debug :acp-proxy/disconnected :url url)
    (some-> @conn* ws/ws-close!)
    (reset! conn* nil)
    (reconnect! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts)))

(defn- poll-event [queue timeout-ms]
  (.poll queue timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS))

(defn- send-request! [conn session-id* url line]
  (cache-session-id! session-id* line)
  (log-proxy-message! url line)
  (ws/ws-send! conn line))

(defn- await-response! [active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts response-id]
  (let [await-poll-ms (or (:acp-proxy-await-poll-ms opts) 50)]
    (loop []
      (let [event (poll-event @remote-queue* await-poll-ms)]
        (case (:type event)
          :message
          (let [message-line (:line event)]
            (cache-session-id! session-id* message-line)
            (write-line! message-line)
            (if (= response-id (request-id message-line))
              :ok
              (recur)))

          :connection-error
          (do
            (connection-lost! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts)
            (write-remote-connection-error! response-id)
            :ok)

          :connection-lost
          (do
            (connection-lost! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts)
            (write-remote-connection-error! response-id)
            :ok)

          (if @disconnected?
            (do
              (write-remote-connection-error! response-id)
              :ok)
            (recur)))))))

(defn- handle-input-line! [active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts line]
  (if @disconnected?
    (do
      (cache-session-id! session-id* line)
      (write-remote-connection-error! (request-id line))
      :ok)
    (try
      (send-request! @conn* session-id* url line)
      (if-let [id (request-id line)]
        (await-response! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts id)
        :ok)
      (catch Exception _
        (connection-lost! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts)
        (write-remote-connection-error! (request-id line))
        :ok))))

(defn- handle-remote-idle-event! [active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts _sent-request? event]
  (case (:type event)
    :message (do
               (cache-session-id! session-id* (:line event))
               (write-line! (:line event))
               :ok)
    :connection-error (do
                        (connection-lost! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts)
                        :ok)
    :connection-lost (do
                       (connection-lost! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts)
                       :ok)
    :ok))

(defn- run-remote [opts]
  (let [opts    (merge {:acp-proxy-reconnect-delay-ms     (or (:acp-proxy-reconnect-delay-ms opts)
                                                              (get-in (config/load-config {:home (or (:home opts) (System/getProperty "user.home"))}) [:acp :proxy-reconnect-delay-ms])
                                                              1000)
                        :acp-proxy-reconnect-max-delay-ms (or (:acp-proxy-reconnect-max-delay-ms opts)
                                                              (get-in (config/load-config {:home (or (:home opts) (System/getProperty "user.home"))}) [:acp :proxy-reconnect-max-delay-ms])
                                                              5000)}
                       opts)
        url     (remote-url opts)
        token   (:token opts)
        factory (or (:ws-connection-factory opts) ws/connect!)]
    (try
      (let [conn*         (atom (connect-remote! factory url token))
            remote-queue* (atom (start-remote-reader! @conn*))
            reconnecting? (atom nil)
            disconnected? (atom false)
            session-id*   (atom (default-session-id opts))
            active?       (atom true)
            input-queue   (start-input-reader! opts)
            eof-grace-ms  (or (:acp-proxy-eof-grace-ms opts) 50)
            main-poll-ms  (or (:acp-proxy-main-poll-ms opts) 10)]
        (log/debug :acp-proxy/connected :url url)
        (let [exit-code (try
                          (loop [stdin-closed-at nil sent-request? false]
                            (if (and stdin-closed-at
                                     (or (not sent-request?)
                                         (<= eof-grace-ms (- (System/currentTimeMillis) stdin-closed-at))))
                               0
                               (if-let [remote-event (poll-event @remote-queue* main-poll-ms)]
                              (if (= :ok (handle-remote-idle-event! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts sent-request? remote-event))
                                 (recur stdin-closed-at sent-request?)
                                 1)
                              (if stdin-closed-at
                                (recur stdin-closed-at sent-request?)
                                (if-let [input-event (poll-event input-queue main-poll-ms)]
                                  (case (:type input-event)
                                    :stdin-closed (recur (System/currentTimeMillis) sent-request?)
                                    :stdin (if (= :ok (handle-input-line! active? conn* remote-queue* reconnecting? disconnected? session-id* factory url token opts (:line input-event)))
                                             (recur nil true)
                                             1)
                                    (recur stdin-closed-at sent-request?))
                                  (recur stdin-closed-at sent-request?))))))
                          (finally
                            (reset! active? false)
                            (some-> @reconnecting? future-cancel)
                            (log/debug :acp-proxy/disconnected :url url)
                            (some-> @conn* ws/ws-close!)))]
          exit-code))
      (catch Exception e
        (print-error! (if (authentication-error? e)
                        "authentication failed"
                        (str "could not connect to remote ACP endpoint: " url)))
        1))))

(defn run [opts]
  (let [agent-id    (or (when (string? (:crew opts)) (:crew opts)) (:agent opts) "main")
        remote-url  (:remote opts)
        model-alias (:model opts)
        session-key (:session opts)
        resume?     (:resume opts)]
    (cond
      (and resume? model-alias)
      (do (print-error! "cannot combine --resume with --model") 1)

      remote-url
      (run-remote opts)

      (= false (ensure-local-config! opts))
      1

      :else
      (let [server-opts  (build-server-opts opts)
            resumed-key  (when resume?
                           (resumed-session-key (:state-dir server-opts) agent-id))
            attached-key (some-> (or session-key resumed-key)
                                 (#(storage/get-session (:state-dir server-opts) %))
                                 :id)
            attach-key   (or attached-key session-key resumed-key)]
        (cond
          (and model-alias (not (valid-model? server-opts model-alias)))
          (do (print-error! (str "unknown model: " model-alias)) 1)

          (and session-key (not (session-exists? (:state-dir server-opts) session-key)))
          (do (print-error! (str "session not found: " session-key)) 1)

          :else
          (let [server-opts' (cond-> server-opts
                               model-alias (assoc :model-override model-alias))
                handlers     (cond-> (server/handlers server-opts')
                               attach-key (attach-session-handler attach-key))]
            (builtin/register-all! tool-registry/register!)
            (print-error! "isaac acp ready")
            (if (:verbose opts)
              (run-loop-verbose handlers)
              (run-loop handlers))
            0))))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "acp")))
        0)

      (seq errors)
      (do
        (doseq [error errors]
          (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name        "acp"
   :usage       "acp [options]"
   :desc        "Run Isaac as an ACP agent over stdio"
   :option-spec option-spec
   :run-fn      run-fn})
