(ns isaac.features.steps.tools
  (:require
    [gherclj.core :as g :refer [defgiven defwhen]]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as registry]))

(defgiven builtin-tools-registered "the built-in tools are registered"
  []
  (registry/clear!)
  (builtin/register-all! registry/register!))

(defwhen tool-executed "tool {name:string} is executed with:"
  [name table]
  (let [args (into {} (map (fn [[k v]] [(keyword k) v]) (:rows table)))
        result (registry/execute name args)]
    (g/assoc! :tool-result result)))
