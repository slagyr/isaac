(ns isaac.hooks
  (:require
    [cheshire.core :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.bridge.core :as bridge]
    [isaac.comm.null :as null-comm]
    [isaac.config.loader :as config]
    [isaac.configurator :as configurator]
    [isaac.fs :as fs]
    [isaac.llm.api :as api]
    [isaac.logger :as log]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]))

;; Holds the future for the most recently dispatched hook turn so test
;; harnesses can await completion via (deref (last-turn-future)).
(defonce last-turn-future* (atom nil))

(defn last-turn-future [] @last-turn-future*)

;; Registry: name → {:source :config|:module, :entry hook-config-or-fn}
(defonce ^:private registry* (atom {}))

(defn reset-registry!
  "Clear the registry. For test isolation."
  []
  (reset! registry* {}))

(defn lookup-hook
  "Return the registry entry for hook name, or nil."
  [name]
  (get @registry* name))

(defn register-hook!
  "Register a hook by name. source is :config or :module.
   Throws on collision (module vs config with same name)."
  [name entry source]
  (let [existing (get @registry* name)]
    (when (and existing (not= (:source existing) source))
      (log/error :hook/collision :name name :existing-source (:source existing) :new-source source)
      (throw (ex-info (str "hook name collision: " name) {:name name}))))
  (swap! registry* assoc name {:source source :entry entry})
  (log/info :hook/registered :name name :source source))

(defn deregister-hook!
  "Remove a hook by name from the registry."
  [name]
  (when (contains? @registry* name)
    (let [entry (get @registry* name)]
      (swap! registry* dissoc name)
      (log/info :hook/deregistered :name name :source (:source entry)))))

(defn reconcile-config-hooks!
  "Reconcile config-declared hooks against the registry.
   old-hooks and new-hooks are the :hooks config maps (may be nil)."
  [old-hooks new-hooks]
  (let [old-names (set (filter string? (keys old-hooks)))
        new-names (set (filter string? (keys new-hooks)))]
    (doseq [name (set/difference old-names new-names)]
      (when (= :config (:source (get @registry* name)))
        (deregister-hook! name)))
    (doseq [name (set/difference new-names old-names)]
      (when-let [hook-cfg (get new-hooks name)]
        (when (map? hook-cfg)
          (register-hook! name hook-cfg :config))))))

;; Reconfigurable implementation

(deftype HooksModule []
  configurator/Reconfigurable
  (on-startup! [_ slice]
    (reconcile-config-hooks! nil slice))
  (on-config-change! [_ old-slice new-slice]
    (reconcile-config-hooks! old-slice new-slice)))

(defn make
  "Factory: creates a HooksModule instance."
  [_host _slice]
  (HooksModule.))

;; Handler

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

(defn handler [request]
  (let [cfg          (config/snapshot)
        state-dir    (system/get :state-dir)
        name         (hook-name (:uri request))]
    (cond
      ;; 1. Auth check — runs even for unknown paths
      (not (auth-ok? cfg request))
      {:status 401 :headers {"Content-Type" "text/plain"} :body "Unauthorized"}

      ;; 2. Method check
      (not= :post (:request-method request))
      {:status 405 :headers {"Content-Type" "text/plain"} :body "Method Not Allowed"}

      ;; 3. Path lookup — from registry
      (nil? (lookup-hook name))
      {:status 404 :headers {"Content-Type" "text/plain"} :body "Not Found"}

      :else
      (let [hook (:entry (lookup-hook name))]
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
              (let [session-store    (or (system/get :session-store)
                                         (file-store/create-store state-dir))
                    crew-id          (or (:crew hook) "main")
                    session-key      (or (:session-key hook) (str "hook:" name))
                    existing-session (store/get-session session-store session-key)
                    home             (some-> state-dir fs/parent)
                    quarters         (str state-dir "/crew/" crew-id)
                    crew-ctx         (config/resolve-crew-context cfg crew-id {:home           home
                                                                               :model-override (:model hook)})
                    template         (:template hook)
                    message          (render-template template body)
                    turn-opts        {:comm           null-comm/channel
                                      :context-window (:context-window crew-ctx)
                                      :model          (:model crew-ctx)
                                      :model-cfg      (:model-cfg crew-ctx)
                                      :provider       (:provider crew-ctx)
                                      :provider-cfg   (:provider-cfg crew-ctx)
                                      :soul           (:soul crew-ctx)}]
                (log/info :hook/dispatch-planned
                          :hook name
                          :session session-key
                          :crew crew-id
                          :model (:model crew-ctx)
                          :provider (some-> (:provider crew-ctx) api/display-name)
                          :cwd (:cwd existing-session)
                          :existing-session? (boolean existing-session)
                          :message-chars (count message)
                          :has-model-override? (some? (:model hook)))
                (when-not existing-session
                  (fs/mkdirs quarters)
                  (store/open-session! session-store session-key
                                       {:crew   crew-id
                                        :cwd    quarters
                                        :origin {:kind :webhook :name name}}))
                (dispatch-turn! session-key message turn-opts)
                {:status 202 :headers {"Content-Type" "text/plain"} :body "Accepted"}))))))))
