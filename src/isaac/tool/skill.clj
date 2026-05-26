(ns isaac.tool.skill
  (:require
    [isaac.config.api :as config]
    [isaac.prompt.catalog :as prompt-catalog]
    [isaac.session.store :as store]
    [isaac.tool.fs-bounds :as bounds]))

(defn- session-entry [args]
  (let [session-key   (get args "session_key")
        session-store (bounds/session-store args)]
    (when session-key
      (some-> session-store
              (store/get-session session-key)))))

(defn- missing-session-error [session-key]
  {:isError true :error (str "session not found: " session-key)})

(defn- catalog-opts [args]
  (let [cfg     (or (config/snapshot "skill tools: prompt catalog resolution") {})
        session (session-entry args)]
    {:config    cfg
     :cwd       (:cwd session)
     :fs        (bounds/filesystem args)
     :state-dir (or (:state-dir cfg) (bounds/state-dir args))}))

(defn load-skill-tool
  "Load the full body of a discovered skill for the calling session."
  [arguments]
  (let [args        (bounds/string-key-map arguments)
        session-key (get args "session_key")
        skill-name  (get args "name")
        resource    (some-> (get args "resource") not-empty)]
    (cond
      (nil? (session-entry args))
      (missing-session-error session-key)

      resource
      {:isError true :error "skill resources are not supported yet"}

      :else
      (if-let [body (prompt-catalog/resolve-skill-body (catalog-opts args) skill-name)]
        {:result body}
        {:isError true :error (str "unknown skill: " skill-name)}))))

(defn list-skills-tool
  "List discovered skills by name and description for the calling session."
  [arguments]
  (let [args        (bounds/string-key-map arguments)
        session-key (get args "session_key")]
    (if (nil? (session-entry args))
      (missing-session-error session-key)
      {:result (or (prompt-catalog/resolve-skill-menu (catalog-opts args))
                   "No skills discovered.")})))

(defn load-skill-tool-factory [_]
  {:description "Load the full body of a discovered skill by name."
   :parameters  {:type       "object"
                 :properties {"name"     {:type "string" :description "Skill name to load"}
                              "resource" {:type "string" :description "Optional bundled resource name for future skill assets"}}
                 :required   ["name"]}
   :handler     #'load-skill-tool})

(defn list-skills-tool-factory [_]
  {:description "List available skills by name and description."
   :parameters  {:type "object" :properties {}}
   :handler     #'list-skills-tool})
