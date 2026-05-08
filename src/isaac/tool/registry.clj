(ns isaac.tool.registry
  (:require
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.system :as system]))

;; region ----- State -----

(defn- registry-atom []
  (or (system/get :tool-registry)
      (let [registry* (atom {})]
        (system/register! :tool-registry registry*)
        registry*)))

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
  (swap! (registry-atom) assoc name tool))

(defn unregister! [name]
  (swap! (registry-atom) dissoc name))

(defn clear! []
  (reset! (registry-atom) {}))

(defn lookup [name]
  (get @(registry-atom) name))

(defn- activate-missing-tool! [module-index name]
  (when-let [module-id (module-loader/supporting-module-id module-index :tool name)]
    (module-loader/activate! module-id module-index)
    (lookup name)))

(defn all-tools
  "With no args, returns every registered tool.
   With an allowed-tools collection, returns only the tools in the allow list.
   A nil allowed-tools with the 1-arity denies every tool (default-deny)."
  ([]
   (vec (vals @(registry-atom))))
  ([allowed-tools]
   (->> (vals @(registry-atom))
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
      (unknown-tool-error name)))
  ([name arguments allowed-tools module-index]
   (if (allowed-tool? allowed-tools name)
     (do
       (when-not (lookup name)
         (activate-missing-tool! module-index name))
       (if (lookup name)
         (run-handler name arguments)
         (unknown-tool-error name)))
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
      (result->string (execute name arguments allowed-tools))))
  ([allowed-tools module-index]
   (fn [name arguments]
     (result->string (execute name arguments allowed-tools module-index)))))

;; endregion ^^^^^ Execution ^^^^^

;; region ----- Prompt Definitions -----

(defn tool-definitions
  "Returns tool definitions suitable for inclusion in an LLM prompt (no handler fn)."
  ([]
   (mapv #(dissoc % :handler) (all-tools)))
  ([allowed-tools]
   (mapv #(dissoc % :handler) (all-tools allowed-tools)))
  ([allowed-tools module-index]
   (doseq [tool-name allowed-tools]
     (when-not (lookup tool-name)
       (activate-missing-tool! module-index tool-name)))
   (mapv #(dissoc % :handler) (all-tools allowed-tools))))

;; endregion ^^^^^ Prompt Definitions ^^^^^
