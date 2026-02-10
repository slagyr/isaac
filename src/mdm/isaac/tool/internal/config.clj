(ns mdm.isaac.tool.internal.config
  "Config tools - get and set runtime configuration settings."
  (:require [mdm.isaac.setting.core :as setting]
            [mdm.isaac.tool.core :as tool]))

(def get-config-tool
  {:name :get-config
   :description "Get a runtime config setting value"
   :params {:key {:type :keyword :required true}}
   :permissions #{:internal :read-only}
   :execute (fn [{:keys [key]}]
              (if (nil? key)
                {:status :error :message "key is required"}
                {:status :ok :value (setting/get key)}))})

(def set-config-tool
  {:name :set-config
   :description "Set a runtime config setting value"
   :params {:key {:type :keyword :required true}
            :value {:type :string :required true}}
   :permissions #{:internal}
   :execute (fn [{:keys [key value]}]
              (cond
                (nil? key) {:status :error :message "key is required"}
                (nil? value) {:status :error :message "value is required"}
                :else (do
                        (setting/set! key value)
                        {:status :ok :value value})))})

(def all-tools
  [get-config-tool
   set-config-tool])

(defn register-all!
  "Register all config tools."
  []
  (doseq [t all-tools]
    (tool/register! t)))
