;; mutation-tested: 2026-05-06
(ns isaac.server.app
  (:require
    [c3kit.apron.refresh :as refresh]
    [clojure.string :as str]
    [isaac.comm.discord :as discord]
    [isaac.comm.registry :as comm-registry]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.session.store :as store]
    [isaac.cron.scheduler :as scheduler]
    [isaac.comm.delivery.worker :as worker]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.configurator :as configurator]
    [isaac.hooks :as hooks]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.system :as system]
    [isaac.server.http :as http]
    [isaac.server.routes :as routes]
    [org.httpkit.server :as httpkit]))

(defonce ^:private state (atom nil))

(declare stop!)

(defn running? []
  (some? @state))

(defn current-config []
  (some-> @state :cfg deref))

(defn registries []
  [(assoc @comm-registry/*registry* :kind :slot-tree)
   hooks/registry
   scheduler/registry])

(defn comm-tree
  "Returns the live object-tree atom (mirrors :comms shape). Returns nil if
   the server is not running."
  []
  (some-> @state :tree))

(defn- dev-handler []
  (refresh/init refresh/services "isaac" [])
  (let [refreshing (refresh/refresh-handler 'isaac.server.http/root-handler)
        scanning   (fn [request]
                     (log/debug :server/dev-reload-scan
                                :method (:request-method request)
                                :uri (:uri request))
                     (refreshing request))]
    (http/wrap-logging scanning)))

(defn- config-error-prefix [path]
  (when-let [[_ kind id] (re-matches #"(crew|models|providers)/([^/]+)\.edn" path)]
    (str kind "." id)))

(defn- reload-failure [path errors]
  (if-let [parse-error (some #(when (= path (:key %)) %) errors)]
    {:reason :parse :error (:value parse-error)}
    (let [prefix           (config-error-prefix path)
          relevant-errors  (cond
                             prefix (filter #(str/starts-with? (:key %) prefix) errors)
                             :else  errors)
          formatted-error  (->> relevant-errors
                                (map #(str (:key %) " " (:value %)))
                                (clojure.string/join "\n"))]
      {:reason :validation :error formatted-error})))

(defn- ->name [x]
  (cond
    (keyword? x) (name x)
    :else        (str x)))

(defn- dotted-path [path]
  (str/join "." (map ->name path)))

(defn- comm-validation-errors [cfg registry]
  (let [path  (:path registry)
        impls (:impls registry)
        mod-index (:module-index cfg)
        cont  (get-in cfg path)]
    (->> cont
         (keep (fn [[slot slice]]
                  (when (map? slice)
                    (let [impl     (configurator/slot-impl slot slice)
                          lazy?    (some #(get-in % [:manifest :comm (keyword (->name impl))])
                                         (vals mod-index))
                          slot-pth (dotted-path (conj (vec path) slot))]
                      (when (and impl (not lazy?) (not (contains? impls (->name impl))))
                        {:path slot-pth :message (str "unknown :impl " (pr-str impl))})))))
          (remove nil?)
          vec)))

(defn validate-config!
  "Logs any comm-impl validation errors against the given registry. Returns
   the seq of errors (empty if cfg is valid)."
  [cfg registry]
  (let [errors (comm-validation-errors cfg registry)]
    (doseq [{:keys [path message]} errors]
      (log/error :config/validation-error :path path :message message))
    errors))

(defn discord-integration
  "Returns the first DiscordIntegration in the comm tree, or nil. For
   single-Discord setups; multi-instance callers should walk the tree
   directly."
  []
  (when-let [tree (comm-tree)]
    (some (fn [[_ inst]] (when (discord/discord-integration? inst) inst))
          (get @tree :comms))))

(defn discord-client []
  (some-> (discord-integration)
          discord/client
          :client))

(defn- reload-config! [config-home cfg* tree* host comm-registry registries path]
  (let [load-result (config/load-config-result {:home config-home :raw-parse-errors? true})
        errors      (:errors load-result)
        new-cfg     (assoc (:config load-result) :module-index (:module-index host))]
    (cond
      (seq errors)
      (let [{:keys [error reason]} (reload-failure path errors)]
        (log/error :config/reload-failed :error error :path path :reason reason))

      (seq (validate-config! new-cfg comm-registry))
      nil

      :else
      (let [old-cfg @cfg*]
        (reset! cfg* new-cfg)
        (config/set-snapshot! new-cfg)
        (configurator/reconcile! tree* host old-cfg new-cfg registries)
        (log/info :config/reloaded :path path)))))

(defn- start-config-reloader! [source config-home cfg* tree* host comm-registry registries]
  (future
    (loop []
      (when-let [path (change-source/poll! source 5000)]
        (reload-config! config-home cfg* tree* host comm-registry registries path))
      (recur))))

(defn- startup-settings [opts]
  {:port               (or (:port opts) 6674)
   :host               (or (:host opts) "0.0.0.0")
   :dev?               (true? (:dev opts))
   :hot-reload?        (not (false? (get-in opts [:cfg :server :hot-reload])))
   :start-http-server? (not (false? (:start-http-server? opts)))
   :state-dir          (:state-dir opts)
   :config-home        (some-> (:state-dir opts) fs/parent)
   :connect-ws!        (:connect-ws! opts)})

(defn- host-context [cfg state-dir connect-ws!]
  {:connect-ws! connect-ws!
   :module-index (:module-index cfg)
   :state-dir state-dir})

(defn- start-config-source [opts hot-reload? config-home]
  (or (:config-change-source opts)
      (when (and hot-reload? config-home)
        (change-source/watch-service-source config-home))))

(defn- build-handler-opts [opts config-home state-dir cfg*]
  (cond-> (dissoc opts :home)
    config-home (assoc :home config-home)
    state-dir   (assoc :state-dir state-dir)
    true        (assoc :cfg-fn (fn [] @cfg*))))

(defn- start-http-server [dev? start-http-server? handler-opts port host]
  (let [handler (when start-http-server?
                  (if dev?
                    (dev-handler)
                    (http/wrap-logging (fn [request]
                                         (routes/handler handler-opts request)))))
        server  (when start-http-server?
                  (httpkit/run-server handler {:port port :ip host :legacy-return-value? false}))
        actual  (if start-http-server? (httpkit/server-port server) port)]
    {:server server :actual actual}))

(defn- start-background-services [_opts state-dir]
  {:delivery (when state-dir
               (worker/start! {}))})

(defn- reset-server-state! [cfg* tree* host-ctx comm-registry registries config-source connect-ws! reloader delivery server actual host start-http-server?]
  (reset! state {:cfg                cfg*
                 :tree               tree*
                 :host-ctx           host-ctx
                 :registry           comm-registry
                 :registries         registries
                 :config-source      config-source
                 :connect-ws!        connect-ws!
                 :reloader           reloader
                 :delivery           delivery
                 :server             server
                 :port               actual
                 :host               host
                 :start-http-server? start-http-server?}))

(defn start! [opts]
  (when (running?) (stop!))
  (let [cfg               (:cfg opts)
        comm-registry     @comm-registry/*registry*
        registries        (registries)
        validation-errors (validate-config! cfg comm-registry)]
    (when-not (seq validation-errors)
      (let [{:keys [port host dev? hot-reload? start-http-server? state-dir config-home connect-ws!]} (startup-settings opts)
            _                        (system/init! {:config (atom cfg)})
            _                        (when state-dir (home/init-state-dir! state-dir) (system/register! :state-dir state-dir) (store/register! cfg state-dir))
            _                        (config/set-snapshot! cfg)
            cfg*                     (atom cfg)
            tree*                    (atom {})
            host-ctx                 (host-context cfg state-dir connect-ws!)
            _                        (configurator/reconcile! tree* host-ctx nil cfg registries)
            _                        (module-loader/register-route-extensions! (get-in (module-loader/core-index) [:isaac.core :manifest]))
            config-source            (start-config-source opts hot-reload? config-home)
            _                        (some-> config-source change-source/start!)
            reloader                 (when (and config-source config-home)
                                       (start-config-reloader! config-source config-home cfg* tree* host-ctx comm-registry registries))
            handler-opts             (build-handler-opts opts config-home state-dir cfg*)
            {:keys [server actual]}  (start-http-server dev? start-http-server? handler-opts port host)
            {:keys [delivery]}       (start-background-services opts state-dir)]
        (when (and dev? start-http-server?)
          (log/info :server/dev-mode-enabled :host host :port actual))
        (reset-server-state! cfg* tree* host-ctx comm-registry registries config-source connect-ws! reloader delivery server actual host start-http-server?)
        {:port actual :host host}))))

(defn stop! []
  (when-let [{:keys [cfg config-source delivery host-ctx registries reloader server tree]} @state]
    (when delivery
      (worker/stop! delivery))
    (when (and tree registries)
      (configurator/reconcile! tree host-ctx @cfg nil registries))
    (some-> reloader future-cancel)
    (when config-source
      (change-source/stop! config-source))
    (when server
      (httpkit/server-stop! server))
    (reset! state nil)))
