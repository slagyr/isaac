(ns isaac.module.tool-test
  (:require
    [isaac.tool.registry :as tool-registry]))

(defonce _registration
  (tool-registry/register! {:name        "echo_mod"
                            :description "Echo from module"
                            :parameters  {:type "object"}
                            :handler     (fn [args] {:result (str "module:" (:msg args))})}))
