(ns isaac.server.hooks
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.drive.turn :as turn]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]))

;; Holds the future for the most recently dispatched hook turn so test
;; harnesses can await completion via (deref (last-turn-future)).
(defonce last-turn-future* (atom nil))

(defn last-turn-future [] @last-turn-future*)

(defn- resolve-cfg [opts]
  (if-let [cfg-fn (:cfg-fn opts)]
    (assoc opts :cfg (cfg-fn))
    opts))

(defn- hook-name [uri]
  (when (str/starts-with? uri "/hooks/")
    (let [name (subs uri (count "/hooks/"))]
      (when-not (str/blank? name) name))))

(defn- bearer-token [request]
  (some-> (get-in request [:headers "authorization"])
          (str/replace-first #"(?i)^Bearer\s+" "")))

(defn- auth-ok? [cfg request]
  (let [expected (get-in cfg [:hooks :auth :token])]
    (or (str/blank? expected)
        (= expected (bearer-token request)))))

(defn- read-body [request]
  (let [body (:body request)]
    (cond
      (nil? body)    ""
      (string? body) body
      :else          (slurp body))))

(defn- render-template [template vars]
  (str/replace template #"\{\{(\w+)\}\}"
               (fn [[_ key]]
                 (let [v (get vars (keyword key))]
                   (if (some? v) (str v) "(missing)")))))

(defn- json-content-type? [request]
  (let [ct (get-in request [:headers "content-type"] "")]
    (str/includes? ct "application/json")))

(defn- dispatch-turn! [state-dir session-key message opts]
  (let [fut (future
              (try
                (turn/process-user-input! state-dir session-key message opts)
                (catch Exception e
                  (log/error :hook/dispatch-error :session session-key :error (.getMessage e)))))]
    (reset! last-turn-future* fut)
    fut))

(defn handler [opts request]
  (let [{:keys [cfg state-dir]} (resolve-cfg opts)
        name                    (hook-name (:uri request))]
    (cond
      ;; 1. Auth check — runs even for unknown paths
      (not (auth-ok? cfg request))
      {:status 401 :headers {"Content-Type" "text/plain"} :body "Unauthorized"}

      ;; 2. Method check
      (not= :post (:request-method request))
      {:status 405 :headers {"Content-Type" "text/plain"} :body "Method Not Allowed"}

      ;; 3. Path lookup
      (nil? (get-in cfg [:hooks name]))
      {:status 404 :headers {"Content-Type" "text/plain"} :body "Not Found"}

      :else
      (let [hook (get-in cfg [:hooks name])]
        (cond
          ;; 4. Content-type check
          (not (json-content-type? request))
          {:status 415 :headers {"Content-Type" "text/plain"} :body "Unsupported Media Type"}

          :else
          (let [body-str (read-body request)
                body     (try (json/parse-string body-str true)
                              (catch Exception _ ::parse-error))]
            (if (= ::parse-error body)
              ;; 5. Body parse error
              {:status 400 :headers {"Content-Type" "text/plain"} :body "Bad Request"}

              ;; 6. Render and dispatch
              (let [crew-id     (or (:crew hook) "main")
                    session-key (or (:session-key hook) (str "hook:" name))
                    sdir        (or state-dir (str (System/getProperty "user.home") "/.isaac"))
                    crew-ctx    (config/resolve-crew-context cfg crew-id {:home sdir})
                    template    (:template hook)
                    message     (render-template template body)
                    turn-opts   {:crew-members    (:crew cfg)
                                 :context-window  (:context-window crew-ctx)
                                 :model           (or (:model hook) (:model crew-ctx))
                                 :models          (:models cfg)
                                 :provider        (:provider crew-ctx)
                                 :provider-config (or (:provider-config crew-ctx) {})
                                 :soul            (:soul crew-ctx)}]
                (when-not (storage/get-session sdir session-key)
                  (storage/create-session! sdir session-key
                                           {:crew   crew-id
                                            :origin {:kind :webhook :name name}}))
                (dispatch-turn! sdir session-key message turn-opts)
                {:status 202 :headers {"Content-Type" "text/plain"} :body "Accepted"}))))))))
