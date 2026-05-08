(ns isaac.slash.builtin
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.session.logging :as logging]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.slash.registry :as slash-registry]))

(defn- session-store [state-dir]
  (file-store/create-store state-dir))

(defn- parse-command [input]
  (let [parts (str/split (str/trim input) #"\s+" 2)
        cmd   (subs (first parts) 1)]
    {:name cmd :args (second parts)}))

(defn- find-alias [models model provider]
  (some (fn [[alias cfg]]
          (when (and (= model (:model cfg)) (= provider (:provider cfg)))
            (if (keyword? alias) (name alias) (str alias))))
        models))

(defn- handle-status [state-dir session-key _input ctx]
  {:type    :command
   :command :status
   :data    (status/status-data state-dir session-key ctx)})

(defn- handle-model [state-dir session-key input ctx]
  (let [{:keys [args]} (parse-command input)
        models         (:models ctx)]
    (if (str/blank? args)
      (let [model    (:model ctx)
            provider (status/ctx-provider-name ctx)
            alias    (or (find-alias models model provider) model)]
        {:type    :command
         :command :model
         :message (str alias " (" provider "/" model ") is the current model")})
      (if-let [model-cfg (or (get models args)
                             (get models (keyword args)))]
        (do
          (store/update-session! (session-store state-dir) session-key {:model    (:model model-cfg)
                                                                        :provider (:provider model-cfg)})
          {:type    :command
           :command :model
           :message (str "switched model to " args " (" (:provider model-cfg) "/" (:model model-cfg) ")")})
        {:type    :command
         :command :unknown
         :message (str "unknown model: " args)}))))

(defn- handle-crew [state-dir session-key input ctx]
  (let [{:keys [args]} (parse-command input)
        current-crew   (or (:crew ctx) "main")
        crew-members   (or (:crew-members ctx) {})]
    (if (str/blank? args)
      {:type    :command
       :command :crew
       :message (str current-crew " is the current crew member")}
      (if (contains? crew-members args)
        (do
          (store/update-session! (session-store state-dir) session-key {:crew     args
                                                                        :model    nil
                                                                        :provider nil})
          (logging/log-crew-changed! session-key current-crew args)
          {:type    :command
           :command :crew
           :message (str "switched crew to " args)})
        {:type    :command
         :command :unknown
         :message (str "unknown crew: " args)}))))

(defn- resolve-cwd-path [state-dir path]
  (cond
    (str/starts-with? path "/") path
    (str/starts-with? path "~/") (str (home/user-home) (subs path 1))
    :else (str state-dir "/" path)))

(defn- handle-cwd [state-dir session-key input _ctx]
  (let [{:keys [args]} (parse-command input)]
    (if (str/blank? args)
      (let [cwd (:cwd (store/get-session (session-store state-dir) session-key))]
        {:type    :command
         :command :cwd
         :message (str "current directory: " (or cwd "(not set)"))})
      (let [resolved (resolve-cwd-path state-dir args)]
        (if (fs/dir? resolved)
          (do
            (store/update-session! (session-store state-dir) session-key {:cwd resolved})
            {:type    :command
             :command :cwd
             :message (str "working directory set to " resolved)})
          {:type    :command
           :command :unknown
           :message (str "no such directory: " args)})))))

(def ^:private built-in-commands
  [{:name        "status"
    :description "Show session status"
    :sort-index  0
    :handler     handle-status}
   {:name        "model"
    :description "Show or switch model"
    :sort-index  1
    :handler     handle-model}
   {:name        "crew"
    :description "Show or switch crew"
    :sort-index  2
    :handler     handle-crew}
   {:name        "cwd"
    :description "Show or set working directory"
    :sort-index  3
    :handler     handle-cwd}])

(defn ensure-registered! []
  (doseq [{:keys [name] :as command} built-in-commands]
    (when-not (slash-registry/registered-command name)
      (slash-registry/register! command))))
