(ns mdm.isaac.setting.core
  "Config settings persistence - get and set tunable application settings."
  (:require [c3kit.bucket.api :as db]))

(defn get
  "Get a config value by key. Returns nil or default if not found."
  ([key] (get key nil))
  ([key default]
   (if-let [config (db/ffind-by :config :key key)]
     (:value config)
     default)))

(defn set!
  "Set a config value. Creates or updates the config entry."
  [key value]
  (if-let [existing (db/ffind-by :config :key key)]
    (db/tx (assoc existing :value value))
    (db/tx {:kind :config :key key :value value})))
