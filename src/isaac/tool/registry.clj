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

(defn- result-preview [result]
  (let [s (str result)]
    (if (> (count s) 200) (subs s 0 200) s)))

(defn execute [name arguments]
  (if-let [tool (lookup name)]
    (do
      (log/debug :tool/start :tool name :arguments arguments)
      (try
        (let [result ((:handler tool) arguments)]
          (cond
            (:isError result)
            (do (log/error :tool/execute-failed :tool name :arguments arguments :error (:error result))
                result)

            (nil? result)
            (do (log/error :tool/execute-failed :tool name :arguments arguments :error "tool returned nil")
                {:isError true :error "tool returned nil"})

            (and (map? result) (contains? result :result))
            (do (log/debug :tool/result :tool name :result (result-preview (:result result)))
                result)

            :else
            (do (log/debug :tool/result :tool name :result (result-preview result))
                {:result result})))
        (catch Exception e
          (log/error :tool/execute-failed :tool name :arguments arguments :error (.getMessage e))
          {:isError true :error (.getMessage e)})))
    (do
      (log/error :tool/execute-failed :tool name :error (str "unknown tool: " name))
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
