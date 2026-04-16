(ns isaac.tool.registry
  (:require [isaac.logger :as log]))

;; region ----- State -----

(defonce ^:private registry (atom {}))

(defn- normalize-allowed-tools [allowed-tools]
  (when (some? allowed-tools)
    (->> allowed-tools
         (map (fn [tool]
                (cond
                  (keyword? tool) (name tool)
                  (string? tool)  tool
                  :else           (str tool))))
         set)))

(defn- allowed-tool? [allowed-tools name]
  (let [allowed-tools (normalize-allowed-tools allowed-tools)]
    (or (nil? allowed-tools)
        (contains? allowed-tools name))))

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

(defn all-tools
  ([]
   (all-tools nil))
  ([allowed-tools]
   (->> (vals @registry)
        (filter #(allowed-tool? allowed-tools (:name %)))
        vec)))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- Execution -----

(defn- result-preview [result]
  (let [s (str result)]
    (if (> (count s) 200) (subs s 0 200) s)))

(defn execute
  ([name arguments]
   (execute name arguments nil))
  ([name arguments allowed-tools]
   (if (allowed-tool? allowed-tools name)
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
         {:isError true :error (str "unknown tool: " name)}))
     (do
       (log/error :tool/execute-failed :tool name :error (str "unknown tool: " name))
       {:isError true :error (str "unknown tool: " name)}))))

(defn tool-fn
  "Returns a function compatible with chat-with-tools that dispatches to the registry."
  ([]
   (tool-fn nil))
  ([allowed-tools]
   (fn [name arguments]
     (let [{:keys [result error isError]} (execute name arguments allowed-tools)]
       (if isError
         (str "Error: " error)
         result)))))

;; endregion ^^^^^ Execution ^^^^^

;; region ----- Prompt Definitions -----

(defn tool-definitions
  "Returns tool definitions suitable for inclusion in an LLM prompt (no handler fn)."
  ([]
   (tool-definitions nil))
  ([allowed-tools]
   (mapv #(dissoc % :handler) (all-tools allowed-tools))))

;; endregion ^^^^^ Prompt Definitions ^^^^^
