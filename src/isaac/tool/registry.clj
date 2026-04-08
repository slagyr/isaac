(ns isaac.tool.registry
  (:require [isaac.logger :as log]))

;; region ----- State -----

(defonce ^:private registry (atom {}))

;; endregion ^^^^^ State ^^^^^

;; region ----- Registration -----

(defn register! [{:keys [name] :as tool}]
  (swap! registry assoc name tool))

(defn unregister! [name]
  (swap! registry dissoc name))

(defn clear! []
  (reset! registry {}))

(defn lookup [name]
  (get @registry name))

(defn all-tools []
  (vec (vals @registry)))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- Execution -----

(defn execute [name arguments]
  (if-let [tool (lookup name)]
    (do
      (log/debug {:event :tool/start :tool name})
      (try
        (let [result ((:handler tool) arguments)]
          (cond
            (:isError result)
            (do (log/error {:event :tool/execute-failed :tool name :error (:error result)})
                result)

            (and (map? result) (contains? result :result))
            (do (log/debug {:event :tool/result :tool name})
                result)

            :else
            (do (log/debug {:event :tool/result :tool name})
                {:result result})))
        (catch Exception e
          (log/error {:event :tool/execute-failed :tool name :error (.getMessage e)})
          {:isError true :error (.getMessage e)})))
    (do
      (log/error {:event :tool/execute-failed :tool name :error (str "unknown tool: " name)})
      {:isError true :error (str "unknown tool: " name)})))

(defn tool-fn
  "Returns a function compatible with chat-with-tools that dispatches to the registry."
  []
  (fn [name arguments]
    (let [{:keys [result error isError]} (execute name arguments)]
      (if isError
        (str "Error: " error)
        result))))

;; endregion ^^^^^ Execution ^^^^^

;; region ----- Prompt Definitions -----

(defn tool-definitions
  "Returns tool definitions suitable for inclusion in an LLM prompt (no handler fn)."
  []
  (mapv #(dissoc % :handler) (all-tools)))

;; endregion ^^^^^ Prompt Definitions ^^^^^
