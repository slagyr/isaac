(ns isaac.api
  (:require
    [isaac.bridge.core :as bridge-impl]
    [isaac.comm :as comm-impl]
    [isaac.comm.registry :as comm-registry]
    [isaac.configurator :as configurator-impl]
    [isaac.llm.api :as api-impl]
    [isaac.module.loader :as module-loader]
    [isaac.session.store :as session-store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]))

(defn- runtime-ctx []
  (select-keys (system/current) [:state-dir :session-store]))

(def Comm
  "Protocol implemented by comm integrations (Discord, Telly, etc.).
   Implement to receive lifecycle callbacks for turns, tool calls, compaction,
   and errors. See isaac.comm/Comm for the full method list."
  comm-impl/Comm)

(def Reconfigurable
  "Protocol implemented by long-running module instances.
   on-startup! is called when the instance is first started;
   on-config-change! is called on every config reload.
   See isaac.configurator/Reconfigurable for method signatures."
  configurator-impl/Reconfigurable)

(defn register-comm!
  "Register a Comm factory under impl-name.
   factory is (fn [host-map] -> Comm-instance) where host-map contains
   :state-dir and :connect-ws!.
   Returns impl-name (normalised to a string). Side-effects the global registry."
  [impl-name factory]
  (comm-registry/register-factory! impl-name factory))

(defn comm-registered?
  "Return true if a Comm factory is registered under impl-name, false otherwise."
  [impl-name]
  (comm-registry/registered? impl-name))

(defn register-provider!
  "Register an Api factory by name (e.g. \"ollama\", \"anthropic\").
   factory is (fn [name cfg] -> Api).
   Returns api-key. Side-effects the global provider registry."
  [api-key factory]
  (api-impl/register! api-key factory))

(defn- store-for [{:keys [state-dir session-store]}]
  (or session-store
      (some-> state-dir file-store/create-store)
      (throw (ex-info "api requires :state-dir or :session-store" {}))))

(defn create-session!
  "Create (or reopen) a session record.
   identifier may be a session name string or an existing session map.
   opts may include :crew, :origin, :chatType, :channel, :cwd.
   Returns the session map."
  ([identifier]
   (session-store/open-session! (store-for (runtime-ctx)) identifier {}))
  ([identifier opts]
   (let [runtime     (merge (runtime-ctx) (select-keys opts [:state-dir :session-store]))
         session-opts (dissoc opts :state-dir :session-store)]
     (session-store/open-session! (store-for runtime) identifier session-opts)))
  ([state-dir identifier opts]
   (session-store/open-session! (store-for {:state-dir state-dir}) identifier opts)))

(defn get-session
  "Return the session map for identifier, or nil if not found.
   identifier may be a session name string, key string, or session map."
  ([identifier]
   (session-store/get-session (store-for (runtime-ctx)) identifier))
  ([state-dir identifier]
   (session-store/get-session (store-for {:state-dir state-dir}) identifier)))

(defn dispatch!
  "Comm-facing entry point for inbound messages. Triage slash commands,
   then delegate normal turns to the bridge dispatcher.
   request must have :session-key and :input; see bridge/dispatch! for full shape."
  ([request]
   (bridge-impl/dispatch! (merge (runtime-ctx) request)))
  ([state-dir request]
   (bridge-impl/dispatch! state-dir request)))

;; Module-loader registration: dispatched by module.loader when activating a
;; manifest's :comm extension.
(module-loader/register-handler! :comm #'register-comm!)
