(ns isaac.tool.registry)

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
    (try
      {:result ((:handler tool) arguments)}
      (catch Exception e
        {:isError true :error (.getMessage e)}))
    {:isError true :error (str "unknown tool: " name)}))

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
