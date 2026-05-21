(ns isaac.system
  (:refer-clojure :exclude [get reset!])
  (:require
    [isaac.logger :as log]))

;; ctx is per-turn; system is the process-wide runtime registry.
;; Runtime code reads the installed root runtime; tests can temporarily install an
;; isolated runtime around an example.

(def schema
  "c3kit schema documenting reserved system keys.
   Modules may register additional state under namespaced keywords (e.g. :my-module/state)."
  {:name        :system
   :type        :map
   :description "Isaac global runtime context"
   :schema      {:state-dir         {:type :string :description "Isaac state directory path"}
                 :server            {:type :ignore :description "HTTP server instance"}
                 :session-store     {:type :ignore :description "Session store instance (isaac-o3da)"}
                 :scheduler         {:type :ignore :description "Shared task scheduler instance"}
                 :config            {:type :ignore :description "Runtime configuration atom or value"}
                 :tool-registry     {:type :ignore :description "Tool registry atom"}
                 :slash-registry    {:type :ignore :description "Slash command registry atom"}
                 :comm-registry     {:type :ignore :description "Comm registry atom"}
                 :provider-registry {:type :ignore :description "Provider registry atom"}
                 :module-index      {:type :ignore :description "Module activation index"}}})

(def ^:private known-keys (set (keys (:schema schema))))

(defonce ^:private root-runtime (atom {}))

(def ^:private default-slots
  {:config        (atom nil)
   :tool-registry (atom {})})

(defn current
  "Returns the currently installed root runtime map."
  []
  @root-runtime)

(defn install!
  "Installs runtime as the current root runtime, replacing the previous map."
  [runtime]
  (clojure.core/reset! root-runtime runtime))

(defn get
  "Returns the value registered under k, or nil."
  [k]
  (clojure.core/get (current) k))

(defn register!
  "Registers value v under key k in the current system.
   Logs a :warn :system/unknown-key when k is not a known schema key and not a namespaced keyword."
  [k v]
  (when (and (not (contains? known-keys k))
             (not (namespace k)))
    (log/warn :system/unknown-key :key k))
  (swap! root-runtime assoc k v))

(defn registered?
  "Returns true if k has been registered in the current system."
  [k]
  (contains? (current) k))

(defn init!
  "Registers the default runtime atoms for the current system.
    Optional overrides replace the defaults for matching keys."
  ([] (init! {}))
  ([overrides]
   (install! (merge (current) default-slots overrides))))

(defn reset!
  "Clears every key from the current system. Test fixtures call this between
    scenarios so registered values (e.g. :session-store) don't leak across
    examples sharing the process root runtime."
  []
  (install! {}))

(defn with-installed* [runtime f]
  (let [previous (current)]
    (try
      (install! runtime)
      (f)
      (finally
        (install! previous)))))

(defn bound-runtime-fn
  "Captures the current root runtime and returns a function that reinstalls it
   when invoked later, including on a different thread."
  [f]
  (let [runtime (current)]
    (fn [& args]
      (with-installed* runtime #(apply f args)))))

(defmacro with-system
  "Temporarily installs m as the root runtime for the duration of body.
   Provides test isolation: mutations inside do not affect the outer runtime."
  [m & body]
  `(with-installed* ~m (fn [] ~@body)))

(defmacro with-nested-system
  "Temporarily installs a runtime that merges m over the current root runtime.
   Unlike with-system, existing slots (:config, :tool-registry, etc.) are
   preserved; only keys in m are overridden. Mutations to top-level keys in the
   nested scope do not bleed back to the outer runtime. Inner atoms stored as
   values (like the :config atom) are shared, so both layers see the same runtime
   state through them."
  [m & body]
  `(with-installed* (merge (current) ~m) (fn [] ~@body)))
