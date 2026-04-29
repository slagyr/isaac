(ns isaac.server.acp-websocket
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as acp-server]
    [isaac.acp.ws :as ws]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [ring.util.codec :as codec]
    [org.httpkit.server :as httpkit]))

(defn- request-client [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (:remote-addr request)
      "unknown"))

(defn- message-method [line]
  (try
    (:method (json/parse-string line true))
    (catch Exception _ nil)))

(defn- event-name [method]
  ({"initialize"     :acp-ws/initialize
    "session/new"    :acp-ws/session-new
    "session/prompt" :acp-ws/session-prompt
    "session/cancel" :acp-ws/session-cancel} method))

(defn- resolve-cfg [opts]
  (if-let [cfg-fn (:cfg-fn opts)]
    (assoc opts :cfg (cfg-fn))
    opts))

(declare server-opts)

(defn- query-params [request]
  (codec/form-decode (or (:query-string request) "")))

(defn- bearer-token [request]
  (some-> (or (get-in request [:headers "authorization"])
              (get-in request [:headers :authorization]))
          (str/replace-first #"(?i)^Bearer\s+" "")))

(defn auth-error-response [{:keys [cfg]} request]
  (let [mode  (get-in cfg [:gateway :auth :mode])
        token (get-in cfg [:gateway :auth :token])]
    (when (and (= "token" mode)
               (not= token (bearer-token request)))
      {:status  401
       :headers {"Content-Type" "text/plain"}
       :body    "authentication failed"})))

(defn- send-json-line! [send-line! message]
  (send-line! (json/generate-string message)))

(defn- send-line! [_request channel line]
  (httpkit/send! channel line))

(defn- resumed-session-key [{:keys [query-params state-dir crew-id]}]
  (when (and state-dir (= "true" (get query-params "resume")))
    (some->> (storage/list-sessions state-dir (or crew-id "main"))
             (sort-by :updated-at)
             last
             :id)))

(defn- log-dispatch! [request message result]
  (when-let [event (event-name (:method message))]
    (let [session-id (or (get-in result [:sessionId])
                         (get-in result [:result :sessionId])
                         (get-in result [:response :result :sessionId])
                         (get-in message [:params :sessionId]))]
      (log/debug event
                 :client (request-client request)
                 :sessionId session-id
                 :uri (:uri request)))))

(defn dispatch-line [opts request line]
  (let [message     (json/parse-string line true)
        server-opts (assoc (server-opts opts) :output-writer (:output-writer opts))
        result      (if-let [session-key (and (= "session/new" (:method message))
                                              (resumed-session-key (assoc opts
                                                                     :crew-id (:crew-id server-opts)
                                                                     :state-dir (:state-dir server-opts))))]
                      (rpc/handle-line (assoc (acp-server/handlers server-opts)
                                         "session/new"
                                         (fn [_ _] {:sessionId session-key}))
                                       line)
                      (acp-server/dispatch-line server-opts line))]
    (log-dispatch! request message result)
    result))

(defn send-dispatch-result! [send-line! result]
  (when result
    (cond
      (contains? result :notifications)
      (do
        (doseq [notification (:notifications result)]
          (send-json-line! send-line! notification))
        (when-let [response (:response result)]
          (send-json-line! send-line! response)))

      (contains? result :response)
      (send-json-line! send-line! (:response result))

      :else
      (send-json-line! send-line! result))))

(defn- server-opts [{:keys [cfg home state-dir] :as opts}]
  (let [home        (or home (System/getProperty "user.home"))
        state-dir   (or state-dir (:stateDir cfg) (str home "/.isaac"))
        query       (:query-params opts)
        crew-id     (or (:crew opts) (get query "crew"))
        model-value (or (:model-override opts) (:model opts) (get query "model"))]
    (cond-> {:cfg cfg :home home :state-dir state-dir}
            (:crew-members opts) (assoc :crew-members (:crew-members opts))
            (:models opts) (assoc :models (:models opts))
            (:provider-configs opts) (assoc :provider-configs (:provider-configs opts))
            crew-id (assoc :crew-id crew-id)
            model-value (assoc :model-override model-value))))

(defn- on-receive! [opts request channel line]
  (let [opts   (resolve-cfg opts)
        writer #(send-line! request channel %)
        result (dispatch-line (assoc opts :output-writer writer) request line)]
    (send-dispatch-result! #(send-line! request channel %) result)))

(defn handler [opts request]
  (let [opts     (assoc opts :query-params (query-params request))
        cfg-opts (resolve-cfg opts)]
    (or (auth-error-response cfg-opts request)
        (if-not (:websocket? request)
          {:status  400
           :headers {"Content-Type" "text/plain"}
           :body    "websocket required"}
          (httpkit/as-channel request {:on-open    (fn [_channel]
                                                     (log/debug :acp-ws/connection-opened
                                                                :client (request-client request)
                                                                :uri (:uri request)))
                                       :on-close   (fn
                                                     ([_channel status]
                                                      (log/debug :acp-ws/connection-closed
                                                                 :client (request-client request)
                                                                 :status status
                                                                 :uri (:uri request)))
                                                     ([_channel status reason]
                                                      (log/debug :acp-ws/connection-closed
                                                                 :client (request-client request)
                                                                 :reason reason
                                                                 :status status
                                                                 :uri (:uri request))))
                                       :on-receive (fn [channel line]
                                                     (on-receive! opts request channel line))})))))
