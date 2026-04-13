(ns isaac.server.acp-websocket
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.acp.server :as acp-server]
    [isaac.acp.ws :as ws]
    [isaac.logger :as log]
    [org.httpkit.server :as httpkit]))

(defn- request-client [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (:remote-addr request)
      "unknown"))

(defn- message-method [line]
  (try
    (:method (json/parse-string line true))
    (catch Exception _ nil)))

(defn- bearer-token [request]
  (some-> (or (get-in request [:headers "authorization"])
              (get-in request [:headers :authorization]))
          (str/replace-first #"(?i)^Bearer\s+" "")))

(defn auth-error-response [{:keys [cfg]} request]
  (let [mode  (get-in cfg [:gateway :auth :mode])
        token (get-in cfg [:gateway :auth :token])]
    (when (and (= "token" mode)
               (not= token (bearer-token request)))
      {:status 401
       :headers {"Content-Type" "text/plain"}
       :body "authentication failed"})))

(defn- send-json-line! [send-line! message]
  (send-line! (json/generate-string message)))

(defn- send-line! [request channel line]
  (log/debug :ws/message-sent
             :client (request-client request)
             :method (message-method line)
             :uri    (:uri request))
  (httpkit/send! channel line))

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
  (let [home      (or home (System/getProperty "user.home"))
        state-dir (or state-dir (:stateDir cfg) (str home "/.isaac"))]
    (cond-> {:cfg cfg :home home :state-dir state-dir}
      (:agents opts) (assoc :agents (:agents opts))
      (:models opts) (assoc :models (:models opts))
      (:provider-configs opts) (assoc :provider-configs (:provider-configs opts))
      (:agent opts) (assoc :agent-id (:agent opts))
      (:model opts) (assoc :model-override (:model opts)))))

(defn- on-receive! [opts request channel line]
  (log/debug :ws/message-received
             :client (request-client request)
             :method (message-method line)
             :uri    (:uri request))
  (let [writer (java.io.StringWriter.)
        result (acp-server/dispatch-line (assoc (server-opts opts) :output-writer writer) line)]
    (doseq [message-line (ws/written-lines writer)]
      (send-line! request channel message-line))
    (send-dispatch-result! #(send-line! request channel %) result)))

(defn handler [opts request]
  (or (auth-error-response opts request)
      (if-not (:websocket? request)
        {:status 400
         :headers {"Content-Type" "text/plain"}
         :body "websocket required"}
        (httpkit/as-channel request {:on-open    (fn [_channel]
                                                   (log/debug :ws/connection-opened
                                                              :client (request-client request)
                                                              :uri    (:uri request)))
                                     :on-close   (fn [_channel status reason]
                                                   (log/debug :ws/connection-closed
                                                              :client (request-client request)
                                                              :reason reason
                                                              :status status
                                                              :uri    (:uri request)))
                                     :on-receive (fn [channel line]
                                                   (on-receive! opts request channel line))}))))
