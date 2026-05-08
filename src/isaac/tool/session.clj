;; mutation-tested: 2026-05-06
(ns isaac.tool.session
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.tool.fs-bounds :as bounds]))

(defn- ->z [ts]
  (when ts
    (if (str/ends-with? ts "Z") ts (str ts "Z"))))

(defn- model-name [m]
  (if (keyword? m) (name m) (when m (str m))))

(defn- resolve-model-alias [session crew-cfg defaults]
  (model-name (or (:model session) (:model crew-cfg) (:model defaults))))

(defn- build-session-state [session model-alias cfg]
  (let [models    (or (:models cfg) {})
        model-cfg (get models model-alias)
        provider  (model-name (:provider model-cfg))]
    {:result (json/generate-string
               {:crew        (or (:crew session) "main")
                :model       {:alias    model-alias
                              :upstream (:model model-cfg)}
                :provider    (or provider "")
                :session     (:id session)
                :cwd         (:cwd session)
                :origin      (or (:origin session) {:kind "cli"})
                :created_at  (->z (:created-at session))
                :updated_at  (->z (:updated-at session))
                :context     {:used   (or (:total-tokens session) 0)
                              :window (:context-window model-cfg)}
                :compactions (or (:compaction-count session) 0)})}))

(defn session-info-tool
  "Report the current session's crew, model, provider, origin, timing, context, and compaction count.
   Args: session_key, state_dir (runtime-injected)."
  [args]
  (let [args        (bounds/string-key-map args)
        session-key (get args "session_key")
        state-dir   (get args "state_dir")
        session     (store/get-session (file-store/create-store state-dir) session-key)]
    (if (nil? session)
      {:isError true :error (str "session not found: " session-key)}
      (let [cfg      (config/load-config {:home (bounds/state-dir->home state-dir)})
            crew-id  (or (:crew session) "main")
            crew-cfg (or (get-in cfg [:crew crew-id]) {})
            defaults (:defaults cfg)]
        (build-session-state session (resolve-model-alias session crew-cfg defaults) cfg)))))

(defn session-model-tool
  "Switch or reset the calling session's model.
   Args: model, reset, session_key, state_dir."
  [args]
  (let [args        (bounds/string-key-map args)
        model       (get args "model")
        reset?      (bounds/arg-bool args "reset" false)
        session-key (get args "session_key")
        state-dir   (get args "state_dir")
        model       (when-not (str/blank? (str model)) model)]
    (cond
      (and model reset?)
      {:isError true :error "model and reset are mutually exclusive"}

      :else
      (let [session-store (file-store/create-store state-dir)
            session       (store/get-session session-store session-key)]
        (if (nil? session)
          {:isError true :error (str "session not found: " session-key)}
          (let [cfg        (config/load-config {:home (bounds/state-dir->home state-dir)})
                crew-id    (or (:crew session) "main")
                crew-cfg   (or (get-in cfg [:crew crew-id]) {})
                defaults   (:defaults cfg)
                models     (or (:models cfg) {})
                crew-alias (model-name (or (:model crew-cfg) (:model defaults)))]
            (cond
              (and model (not (contains? models model)))
              {:isError true :error (str "unknown model: " model)}

              model
              (do
                (store/update-session! session-store session-key {:model model
                                                                  :compaction-disabled false
                                                                  :compaction {:consecutive-failures 0}})
                (build-session-state (assoc session :model model) model cfg))

              reset?
              (do
                (store/update-session! session-store session-key {:model crew-alias
                                                                  :compaction-disabled false
                                                                  :compaction {:consecutive-failures 0}})
                (build-session-state (assoc session :model crew-alias) crew-alias cfg))

              :else
              (build-session-state session (resolve-model-alias session crew-cfg defaults) cfg))))))))
