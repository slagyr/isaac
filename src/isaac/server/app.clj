(ns isaac.server.app
  (:require
    [c3kit.apron.refresh :as refresh]
    [clojure.string :as str]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.cron.scheduler :as scheduler]
    [isaac.delivery.worker :as worker]
    [isaac.logger :as log]
    [isaac.server.routes :as routes]
    [isaac.server.http :as http]
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

(defn- reload-config! [home cfg* path]
  (let [load-result (config/load-config-result {:home home :raw-parse-errors? true})
        errors      (:errors load-result)]
    (if (seq errors)
      (let [{:keys [error reason]} (reload-failure path errors)]
        (log/error :config/reload-failed :error error :path path :reason reason))
      (do
        (reset! cfg* (:config load-result))
        (log/info :config/reloaded :path path)))))

(defn- start-config-reloader! [source home cfg*]
  (future
    (loop []
      (when-let [path (change-source/poll! source 5000)]
        (reload-config! home cfg* path))
      (recur))))

(defn start! [opts]
  (when (running?) (stop!))
  (let [port          (or (:port opts) 6674) ;; 6.674 is Newton's gravitational constant
        host          (or (:host opts) "0.0.0.0")
        dev?          (true? (:dev opts))
        hot-reload?   (not (false? (get-in opts [:cfg :server :hot-reload])))
        cfg*          (atom (:cfg opts))
        config-source (or (:config-change-source opts)
                          (when (and hot-reload?
                                     (or (:state-dir opts) (:home opts)))
                            (change-source/watch-service-source (or (:state-dir opts) (:home opts)))))
        _             (some-> config-source change-source/start!)
        reloader      (when (and config-source (or (:home opts) (:state-dir opts)))
                        (start-config-reloader! config-source (or (:home opts) (:state-dir opts)) cfg*))
        handler       (if dev?
                        (dev-handler)
                        (http/wrap-logging (fn [request]
                                             (routes/handler (assoc opts :cfg-fn (fn [] @cfg*)) request))))
        server        (httpkit/run-server handler {:port port :ip host :legacy-return-value? false})
        actual        (httpkit/server-port server)
        delivery      (when-let [state-dir (:state-dir opts)]
                        (worker/start! {:state-dir state-dir}))
        cron          (when (seq (get-in opts [:cfg :cron]))
                        (scheduler/start! {:cfg       (:cfg opts)
                                           :state-dir (:state-dir opts)}))]
    (when dev?
      (log/info :server/dev-mode-enabled :host host :port actual))
    (reset! state {:cfg           cfg*
                   :config-source config-source
                   :reloader      reloader
                   :cron          cron
                   :delivery      delivery
                   :server        server
                   :port          actual
                   :host          host})
    {:port actual :host host}))

(defn stop! []
  (when-let [{:keys [config-source cron delivery reloader server]} @state]
    (when cron
      (scheduler/stop! cron))
    (when delivery
      (worker/stop! delivery))
    (some-> reloader future-cancel)
    (when config-source
      (change-source/stop! config-source))
    (httpkit/server-stop! server)
    (reset! state nil)))
