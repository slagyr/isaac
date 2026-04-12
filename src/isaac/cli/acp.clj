(ns isaac.cli.acp
  (:require
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as server]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]))

(defn- build-server-opts [opts]
  (let [cfg       (config/load-config)
        sdir      (or (:state-dir opts) (:stateDir cfg)
                      (str (System/getProperty "user.home") "/.isaac"))
        agents    (or (:agents opts)
                      (->> (get-in cfg [:agents :list] [])
                           (into {} (map (fn [a] [(:id a) a])))))
        models    (or (:models opts) (get-in cfg [:agents :models] {}))
        prov-cfgs (or (:provider-configs opts)
                      (->> (get-in cfg [:models :providers] [])
                           (into {} (map (fn [p] [(:name p) p])))))]
    {:state-dir        sdir
     :agents           agents
     :models           models
     :provider-configs prov-cfgs}))

(defn- write-result! [result]
  (when result
    (cond
      (contains? result :notifications)
      (do (doseq [n (:notifications result)]
            (rpc/write-message! *out* n))
          (when-let [r (:response result)]
            (rpc/write-message! *out* r)))

      (contains? result :response)
      (rpc/write-message! *out* (:response result))

      :else
      (rpc/write-message! *out* result))))

(defn run [opts]
  (let [server-opts (build-server-opts opts)
        handlers    (server/handlers server-opts)
        reader      (java.io.BufferedReader. *in*)]
    (loop []
      (when-let [line (.readLine reader)]
        (write-result! (rpc/handle-line handlers line))
        (recur)))
    0))

(registry/register!
  {:name   "acp"
   :usage  "acp"
   :desc   "Run Isaac as an ACP agent over stdio"
   :run-fn run})
