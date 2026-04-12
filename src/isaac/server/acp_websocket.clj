(ns isaac.server.acp-websocket
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.acp.server :as acp-server]
    [isaac.acp.ws :as ws]
    [org.httpkit.server :as httpkit]))

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

(defn- on-receive! [opts channel line]
  (let [writer (java.io.StringWriter.)
        result (acp-server/dispatch-line (assoc (server-opts opts) :output-writer writer) line)]
    (doseq [message-line (ws/written-lines writer)]
      (httpkit/send! channel message-line))
    (send-dispatch-result! #(httpkit/send! channel %) result)))

(defn handler [opts request]
  (or (auth-error-response opts request)
      (if-not (:websocket? request)
        {:status 400
         :headers {"Content-Type" "text/plain"}
         :body "websocket required"}
        (httpkit/as-channel request {:on-receive (fn [channel line]
                                                   (on-receive! opts channel line))}))))
