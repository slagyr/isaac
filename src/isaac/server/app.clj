(ns isaac.server.app
  (:require
    [c3kit.apron.refresh :as refresh]
    [clojure.string :as str]
    [isaac.comm.discord :as discord]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.cron.scheduler :as scheduler]
    [isaac.delivery.worker :as worker]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.plugin :as plugin]
    [isaac.server.http :as http]
    [isaac.server.routes :as routes]
    [org.httpkit.server :as httpkit]))

(defonce ^:private state (atom nil))

(declare stop!)

(defn running? []
  (some? @state))

(defn current-config []
  (some-> @state :cfg deref))

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

(defn- discord-token [cfg]
  (get-in cfg [:comms :discord :token]))

(defn discord-integration []
  (some->> (:plugins @state)
           (some #(when (discord/discord-integration? %) %))))

(defn discord-client []
  (some-> (discord-integration)
          discord/client
          :client))

(defn- reload-config! [config-home cfg* plugins path]
  (let [load-result (config/load-config-result {:home config-home :raw-parse-errors? true})
        errors      (:errors load-result)]
    (if (seq errors)
      (let [{:keys [error reason]} (reload-failure path errors)]
        (log/error :config/reload-failed :error error :path path :reason reason))
      (let [old-cfg @cfg*
            new-cfg (:config load-result)]
        (reset! cfg* new-cfg)
        (plugin/sync-config! plugins old-cfg new-cfg)
        (log/info :config/reloaded :path path)))))

(defn- start-config-reloader! [source config-home cfg* plugins]
  (future
    (loop []
      (when-let [path (change-source/poll! source 5000)]
        (reload-config! config-home cfg* plugins path))
      (recur))))

(defn start! [opts]
  (when (running?) (stop!))
  (let [port               (or (:port opts) 6674) ;; 6.674 is Newton's gravitational constant
        host               (or (:host opts) "0.0.0.0")
        dev?               (true? (:dev opts))
        hot-reload?        (not (false? (get-in opts [:cfg :server :hot-reload])))
        start-http-server? (not (false? (:start-http-server? opts)))
        cfg*               (atom (:cfg opts))
        state-dir          (:state-dir opts)
        config-home        (some-> state-dir fs/parent)
        connect-ws!        (:connect-ws! opts)
        plugins            (plugin/build-all {:state-dir state-dir :connect-ws! connect-ws!})
        config-source      (or (:config-change-source opts)
                                (when (and hot-reload?
                                           config-home)
                                  (change-source/watch-service-source config-home)))
        _                  (some-> config-source change-source/start!)
        reloader           (when (and config-source config-home)
                             (start-config-reloader! config-source config-home cfg* plugins))
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
                             (scheduler/start! {:cfg       (:cfg opts)
                                                :state-dir state-dir}))]
    (plugin/start! plugins (:cfg opts))
    (when (and dev? start-http-server?)
      (log/info :server/dev-mode-enabled :host host :port actual))
    (reset! state {:cfg                cfg*
                    :config-source      config-source
                    :connect-ws!        connect-ws!
                    :plugins            plugins
                    :reloader           reloader
                    :cron               cron
                    :delivery           delivery
                    :server             server
                    :port               actual
                    :host               host
                    :start-http-server? start-http-server?})
    {:port actual :host host}))

(defn stop! []
  (when-let [{:keys [cfg config-source cron delivery plugins reloader server]} @state]
    (when cron
      (scheduler/stop! cron))
    (when delivery
      (worker/stop! delivery))
    (plugin/stop! plugins @cfg)
    (some-> reloader future-cancel)
    (when config-source
      (change-source/stop! config-source))
    (when server
      (httpkit/server-stop! server))
    (reset! state nil)))
