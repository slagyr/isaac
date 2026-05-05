(ns isaac.server.app
  (:require
    [c3kit.apron.refresh :as refresh]
    [clojure.string :as str]
    [isaac.comm.discord :as discord]
    [isaac.comm.registry :as comm-registry]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.cron.scheduler :as scheduler]
    [isaac.delivery.worker :as worker]
    [isaac.fs :as fs]
    [isaac.lifecycle :as lifecycle]
    [isaac.logger :as log]
    [isaac.server.http :as http]
    [isaac.server.routes :as routes]
    [org.httpkit.server :as httpkit]))

(defonce ^:private state (atom nil))

(declare stop!)

(defn running? []
  (some? @state))

(defn current-config []
  (some-> @state :cfg deref))

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
                    (let [impl     (lifecycle/slot-impl slot slice)
                          lazy?    (some #(get-in % [:manifest :extends :comm (keyword (->name impl))])
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

(defn- reload-config! [config-home cfg* tree* host registry path]
  (let [load-result (config/load-config-result {:home config-home :raw-parse-errors? true})
        errors      (:errors load-result)]
    (cond
      (seq errors)
      (let [{:keys [error reason]} (reload-failure path errors)]
        (log/error :config/reload-failed :error error :path path :reason reason))

      (seq (validate-config! (:config load-result) registry))
      nil

      :else
      (let [old-cfg @cfg*
            new-cfg (:config load-result)]
        (reset! cfg* new-cfg)
        (lifecycle/reconcile! tree* host old-cfg new-cfg registry)
        (log/info :config/reloaded :path path)))))

(defn- start-config-reloader! [source config-home cfg* tree* host registry]
  (future
    (loop []
      (when-let [path (change-source/poll! source 5000)]
        (reload-config! config-home cfg* tree* host registry path))
      (recur))))

(defn start! [opts]
  (when (running?) (stop!))
  (let [cfg                (:cfg opts)
        registry           @comm-registry/*registry*
        validation-errors  (validate-config! cfg registry)]
    (when-not (seq validation-errors)
      (let [port               (or (:port opts) 6674) ;; 6.674 is Newton's gravitational constant
            host               (or (:host opts) "0.0.0.0")
            dev?               (true? (:dev opts))
            hot-reload?        (not (false? (get-in opts [:cfg :server :hot-reload])))
            start-http-server? (not (false? (:start-http-server? opts)))
            cfg*               (atom cfg)
            tree*              (atom {})
            state-dir          (:state-dir opts)
            config-home        (some-> state-dir fs/parent)
            connect-ws!        (:connect-ws! opts)
            host-ctx           {:connect-ws! connect-ws!
                                :module-index (:module-index cfg)
                                :state-dir state-dir}
            _                  (lifecycle/reconcile! tree* host-ctx nil cfg registry)
            config-source      (or (:config-change-source opts)
                                    (when (and hot-reload?
                                               config-home)
                                      (change-source/watch-service-source config-home)))
            _                  (some-> config-source change-source/start!)
            reloader           (when (and config-source config-home)
                                 (start-config-reloader! config-source config-home cfg* tree* host-ctx registry))
            handler-opts       (cond-> (dissoc opts :home)
                                 config-home (assoc :home config-home)
                                 state-dir   (assoc :state-dir state-dir)
                                 true        (assoc :cfg-fn (fn [] @cfg*)))
            handler            (when start-http-server?
                                 (if dev?
                                   (dev-handler)
                                    (http/wrap-logging (fn [request]
                                                          (routes/handler handler-opts request)))))
            server             (when start-http-server?
                                 (httpkit/run-server handler {:port port :ip host :legacy-return-value? false}))
            actual             (if start-http-server? (httpkit/server-port server) port)
            delivery           (when state-dir
                                 (worker/start! {:state-dir state-dir}))
            cron               (when (seq (get-in opts [:cfg :cron]))
                                 (scheduler/start! {:cfg       cfg
                                                    :state-dir state-dir}))]
        (when (and dev? start-http-server?)
          (log/info :server/dev-mode-enabled :host host :port actual))
        (reset! state {:cfg                cfg*
                       :tree               tree*
                       :host-ctx           host-ctx
                       :registry           registry
                       :config-source      config-source
                       :connect-ws!        connect-ws!
                       :reloader           reloader
                       :cron               cron
                       :delivery           delivery
                       :server             server
                       :port               actual
                       :host               host
                       :start-http-server? start-http-server?})
        {:port actual :host host}))))

(defn stop! []
  (when-let [{:keys [cfg config-source cron delivery host-ctx registry reloader server tree]} @state]
    (when cron
      (scheduler/stop! cron))
    (when delivery
      (worker/stop! delivery))
    (when (and tree registry)
      (lifecycle/reconcile! tree host-ctx @cfg nil registry))
    (some-> reloader future-cancel)
    (when config-source
      (change-source/stop! config-source))
    (when server
      (httpkit/server-stop! server))
    (reset! state nil)))
