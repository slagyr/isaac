;; mutation-tested: 2026-05-06
(ns isaac.server.app
  (:require
    [c3kit.apron.refresh :as refresh]
    [clojure.string :as str]
    [isaac.comm.registry :as comm-registry]
    [isaac.config.api :as config]
    [isaac.cron.service :as cron-service]
    [isaac.comm.delivery.worker :as worker]
    [isaac.fs :as fs]
    [isaac.hail.bands :as hail-bands]
    [isaac.hail.delivery-worker :as hail-delivery-worker]
    [isaac.hail.router :as hail-router]
    [isaac.home :as home]
    [isaac.hooks :as hooks]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.scheduler :as scheduler-core]
    [isaac.nexus :as nexus]
    [isaac.server.http :as http]
    [isaac.server.routes :as routes]
    [org.httpkit.server :as httpkit]))

(defonce state (atom nil))

(declare stop!)

(defn running? []
  (some? @state))

(defn current-config []
  (config/snapshot "server/current-config accessor"))

(defn registries []
  [(assoc @comm-registry/*registry* :kind :slot-tree)
   hail-bands/registry
   hooks/registry
   cron-service/registry])


(defn- dev-handler [handler-opts]
  (refresh/init refresh/services "isaac" [])
  (let [refreshing (refresh/refresh-handler 'isaac.server.http/root-handler)
        scanning   (fn [request]
                     (log/debug :server/dev-reload-scan
                                 :method (:request-method request)
                                 :uri (:uri request))
                     (refreshing request))]
    (http/wrap-logging (http/wrap-auth handler-opts scanning))))

(defn- start-config-reloader! [source state-dir host comm-registry registries]
  ;; The reloader manages the live runtime: config/reload! reconciles components
  ;; directly into the (global) nexus, so we must NOT capture+restore a runtime
  ;; snapshot the way bound-runtime-fn does for one-shot deferred work — that
  ;; would discard the reconcile. bound-fn still propagates dynamic var bindings.
  (let [reload! (bound-fn [path]
                  (config/reload! {:state-dir     state-dir
                                   :fs            (fs/instance)
                                   :old-config    (config/snapshot "reload: previous config for the reconcile diff")
                                   :comm-registry comm-registry
                                   :registries    registries
                                   :host          host
                                   :path          path}))]
    (future
      (loop []
        (when-let [path (config/poll! source 5000)]
          (reload! path))
        (recur)))))

(defn- startup-settings [opts]
  {:port               (or (:port opts) 6674)
   :host               (or (:host opts) "127.0.0.1")
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

(defn- start-config-source [opts hot-reload? state-dir]
  (or (:config-change-source opts)
      (when (and hot-reload? state-dir)
        (config/watch-service-source state-dir))))

(defn- build-handler-opts [opts config-home state-dir]
  (cond-> (dissoc opts :home)
    config-home (assoc :home config-home)
    state-dir   (assoc :state-dir state-dir)
    true        (assoc :cfg-fn (fn [] (config/snapshot "http handler: ambient config")))))

(defn- start-http-server [dev? start-http-server? handler-opts port host]
  (let [handler (when start-http-server?
                  (if dev?
                    (dev-handler handler-opts)
                    (http/create-handler handler-opts)))
        server  (when start-http-server?
                  (httpkit/run-server handler {:port port :ip host :legacy-return-value? false}))
        actual  (if start-http-server? (httpkit/server-port server) port)]
    {:server server :actual actual}))

(defn- auth-required? [cfg host start-http-server?]
  (and start-http-server?
       (not (http/loopback-host? host))
       (str/blank? (get-in cfg [:server :auth :token]))))

(defn- start-background-services [_opts scheduler]
  {:delivery    (when scheduler
                  (worker/start! {}))
   :hail-delivery (when scheduler
                    (hail-delivery-worker/start! {}))
   :hail-router (when scheduler
                  (hail-router/start! {}))})

(defn- reset-server-state! [host-ctx comm-registry registries config-source connect-ws! reloader scheduler delivery hail-delivery hail-router server actual host start-http-server?]
  (reset! state {:host-ctx           host-ctx
                 :registry           comm-registry
                 :registries         registries
                 :config-source      config-source
                 :connect-ws!        connect-ws!
                 :reloader           reloader
                 :scheduler          scheduler
                 :delivery           delivery
                 :hail-delivery      hail-delivery
                 :hail-router        hail-router
                 :server             server
                 :port               actual
                 :host               host
                 :start-http-server? start-http-server?}))

(defn start! [opts]
  (when (running?) (stop!))
  (let [cfg               (:cfg opts)
        comm-registry     @comm-registry/*registry*
        registries        (registries)
        validation-errors (config/validate-config! cfg comm-registry)]
    (when-not (seq validation-errors)
      (let [{:keys [port host dev? hot-reload? start-http-server? state-dir config-home connect-ws!]}
            (startup-settings opts)]
        (when (auth-required? cfg host start-http-server?)
          (log/error :server/auth-required
                     :host host
                     :message "missing :server :auth :token for non-loopback bind"))
        (when-not (auth-required? cfg host start-http-server?)
          (let [cfg                     (cond-> cfg state-dir (assoc :state-dir state-dir))
                _                       (nexus/init! {:fs (or (fs/instance opts) (fs/real-fs))})
                _                       (when state-dir
                                          (home/init-state-dir! state-dir))
                scheduler               (when state-dir
                                          (-> (scheduler-core/create {})
                                              scheduler-core/start!))
                _                       (when scheduler
                                          (nexus/register! [:scheduler] scheduler))
                host-ctx                (host-context cfg state-dir connect-ws!)
                _                       (config/dangerously-install-config! cfg "server boot")
                _                       (config/install! {:config cfg :registries registries :host host-ctx})
                _                       (module-loader/register-route-extensions! (get-in (module-loader/core-index) [:isaac.core :manifest]))
                _                       (doseq [[_mod-id entry] (:module-index cfg)]
                                          (module-loader/register-route-extensions! (:manifest entry)))
                config-source           (start-config-source opts hot-reload? state-dir)
                _                       (some-> config-source config/start!)
                reloader                (when (and config-source state-dir)
                                          (start-config-reloader! config-source state-dir host-ctx comm-registry registries))
                handler-opts            (build-handler-opts opts config-home state-dir)
                {:keys [server actual]} (start-http-server dev? start-http-server? handler-opts port host)
                {:keys [delivery hail-delivery hail-router]} (start-background-services opts scheduler)]
            (when (and dev? start-http-server?)
              (log/info :server/dev-mode-enabled :host host :port actual))
            (reset-server-state! host-ctx comm-registry registries config-source connect-ws! reloader scheduler delivery hail-delivery hail-router server actual host start-http-server?)
            {:port actual :host host}))))))

(defn stop! []
  (when-let [{:keys [config-source scheduler delivery hail-delivery hail-router host-ctx registries reloader server]} @state]
    (when delivery
      (worker/stop! delivery))
    (when hail-delivery
      (hail-delivery-worker/stop! hail-delivery))
    (when hail-router
      (hail-router/stop! hail-router))
    (when scheduler
      (scheduler-core/shutdown! scheduler))
    (when registries
      (config/reconcile! host-ctx (config/snapshot "shutdown: current config for teardown reconcile") nil registries))
    (some-> reloader future-cancel)
    (when config-source
      (config/stop! config-source))
    (when server
      (if (fn? server)
        (server)
        (httpkit/server-stop! server)))
    (reset! state nil)))
