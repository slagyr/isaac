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
  (when-let [allowed-tools (normalize-allowed-tools allowed-tools)]
    (contains? allowed-tools name)))

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
  "With no args, returns every registered tool.
   With an allowed-tools collection, returns only the tools in the allow list.
   A nil allowed-tools with the 1-arity denies every tool (default-deny)."
  ([]
   (vec (vals @registry)))
  ([allowed-tools]
   (->> (vals @registry)
        (filter #(allowed-tool? allowed-tools (:name %)))
        vec)))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- Execution -----

(defn- result-metadata [result]
  {:result-chars (count (str result))
   :result-type  (cond
                   (string? result) :string
                   (map? result)    :map
                   (vector? result) :vector
                   (sequential? result) :seq
                   (nil? result)    :nil
                   :else            :other)})

(defn- log-arguments [arguments]
  (into {}
        (map (fn [[k v]]
               [(if (string? k) (keyword k) k) v]))
        arguments))

(defn- unknown-tool-error [name]
  (log/error :tool/execute-failed :tool name :error (str "unknown tool: " name))
  {:isError true :error (str "unknown tool: " name)})

(defn- run-handler [name arguments]
  (if-let [tool (lookup name)]
    (do
      (log/debug :tool/start :tool name :arguments (log-arguments arguments))
      (try
        (let [result ((:handler tool) arguments)]
          (cond
            (:isError result)
            (do (log/error :tool/execute-failed :tool name :arguments (log-arguments arguments) :error (:error result))
                result)

            (nil? result)
            (do (log/error :tool/execute-failed :tool name :arguments (log-arguments arguments) :error "tool returned nil")
                {:isError true :error "tool returned nil"})

            (and (map? result) (contains? result :result))
            (do (log/debug :tool/result (assoc (result-metadata (:result result)) :tool name))
                result)

            :else
            (do (log/debug :tool/result (assoc (result-metadata result) :tool name))
                {:result result})))
        (catch Exception e
          (log/error :tool/execute-failed :tool name :arguments (log-arguments arguments) :error (.getMessage e))
          {:isError true :error (.getMessage e)})))
    (unknown-tool-error name)))

(defn execute
  ([name arguments]
   (run-handler name arguments))
  ([name arguments allowed-tools]
   (if (allowed-tool? allowed-tools name)
     (run-handler name arguments)
     (unknown-tool-error name))))

(defn- result->string [{:keys [result error isError]}]
  (if isError
    (str "Error: " error)
    result))

(defn tool-fn
  "Returns a function compatible with chat-with-tools that dispatches to the registry."
  ([]
   (fn [name arguments]
     (result->string (execute name arguments))))
  ([allowed-tools]
   (fn [name arguments]
     (result->string (execute name arguments allowed-tools)))))

;; endregion ^^^^^ Execution ^^^^^

;; region ----- Prompt Definitions -----

(defn tool-definitions
  "Returns tool definitions suitable for inclusion in an LLM prompt (no handler fn)."
  ([]
   (mapv #(dissoc % :handler) (all-tools)))
  ([allowed-tools]
   (mapv #(dissoc % :handler) (all-tools allowed-tools))))

;; endregion ^^^^^ Prompt Definitions ^^^^^
