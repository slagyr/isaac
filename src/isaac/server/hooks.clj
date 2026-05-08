;; mutation-tested: 2026-05-06
(ns isaac.server.hooks
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.bridge.core :as bridge]
    [isaac.comm.null :as null-comm]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]))

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

(defn- dispatch-turn! [session-key message opts]
  (let [fut (future
              (try
                (bridge/dispatch! (assoc opts :session-key session-key :input message))
                (catch Exception e
                  (log/error :hook/dispatch-error :session session-key :error (.getMessage e)))))]
    (reset! last-turn-future* fut)
    fut))

(defn handler [opts request]
  (let [{:keys [cfg state-dir]} (resolve-cfg opts)
        name                    (hook-name (:uri request))
        run!                    (fn []
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
                                            (let [sdir          (system/get :state-dir)
                                                  session-store (file-store/create-store sdir)
                                                  crew-id     (or (:crew hook) "main")
                                                  session-key (or (:session-key hook) (str "hook:" name))
                                                  home        (some-> sdir fs/parent)
                                                  crew-ctx    (config/resolve-crew-context cfg crew-id {:home home})
                                                  template    (:template hook)
                                                  message     (render-template template body)
                                                  turn-opts   {:comm           null-comm/channel
                                                               :context-window (:context-window crew-ctx)
                                                               :model          (or (:model hook) (:model crew-ctx))
                                                               :provider       (:provider crew-ctx)
                                                               :soul           (:soul crew-ctx)}]
                                              (when-not (store/get-session session-store session-key)
                                                (store/open-session! session-store session-key
                                                                     {:crew   crew-id
                                                                      :origin {:kind :webhook :name name}}))
                                              (dispatch-turn! session-key message turn-opts)
                                              {:status 202 :headers {"Content-Type" "text/plain"} :body "Accepted"})))))))]
    (if state-dir
      (system/with-system {:state-dir state-dir} (run!))
      (run!))))
