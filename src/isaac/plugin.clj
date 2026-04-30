(ns isaac.plugin)

(defprotocol Plugin
  (config-path [this])
  (on-config-change! [this old new]))

(defonce ^:private builders (atom []))

(defn clear-builders! []
  (reset! builders []))

(defn register! [builder]
  (swap! builders conj builder)
  builder)

(defn build-all [ctx]
  (mapv #(% ctx) @builders))

(defn- config-slice [cfg plugin]
  (get-in cfg (config-path plugin)))

(defn sync-config! [plugins old-cfg new-cfg]
  (doseq [plugin plugins
          :let [old-slice (config-slice old-cfg plugin)
                new-slice (config-slice new-cfg plugin)]
          :when (not= old-slice new-slice)]
    (on-config-change! plugin old-slice new-slice)))

(defn start! [plugins cfg]
  (sync-config! plugins nil cfg))

(defn stop! [plugins cfg]
  (sync-config! plugins cfg nil))
