(ns isaac.system
  (:require
    [isaac.logger :as log]))

;; ctx is per-turn; system is the global runtime registry.
;; Tests bind a fresh system atom via with-system; production uses the default atom.

(def schema
  "c3kit schema documenting reserved system keys.
   Modules may register additional state under namespaced keywords (e.g. :my-module/state)."
  {:name        :system
   :type        :map
   :description "Isaac global runtime context"
   :schema      {:state-dir         {:type :string :description "Isaac state directory path"}
                 :server            {:type :ignore :description "HTTP server instance"}
                 :session-store     {:type :ignore :description "Session store instance (isaac-o3da)"}
                 :config            {:type :ignore :description "Runtime configuration atom or value"}
                 :tool-registry     {:type :ignore :description "Tool registry atom"}
                 :slash-registry    {:type :ignore :description "Slash command registry atom"}
                 :comm-registry     {:type :ignore :description "Comm registry atom"}
                 :provider-registry {:type :ignore :description "Provider registry atom"}
                 :active-turns      {:type :ignore :description "Active turn cancellation map atom"}
                 :module-index      {:type :ignore :description "Module activation index"}}})

(def ^:private known-keys (set (keys (:schema schema))))

(defonce ^:private default-system (atom {}))

(def ^:dynamic *system* default-system)

(defn get
  "Returns the value registered under k, or nil."
  [k]
  (clojure.core/get @*system* k))

(defn register!
  "Registers value v under key k in the current system.
   Logs a :warn :system/unknown-key when k is not a known schema key and not a namespaced keyword."
  [k v]
  (when (and (not (contains? known-keys k))
             (not (namespace k)))
    (log/warn :system/unknown-key :key k))
  (swap! *system* assoc k v))

(defn registered?
  "Returns true if k has been registered in the current system."
  [k]
  (contains? @*system* k))

(defmacro with-system
  "Binds *system* to a fresh atom initialized with m for the duration of body.
   Provides test isolation: mutations inside do not affect the outer system."
  [m & body]
  `(binding [*system* (atom ~m)]
     ~@body))
