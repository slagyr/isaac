(ns isaac.features.steps.context
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen helper!]]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.context :as session-ctx]))

(helper! isaac.features.steps.context)

(defn- build-synthetic-cfg [agents models]
  (let [crew          (into {} (map (fn [[id a]]
                                      [id (cond-> {}
                                            (:soul a) (assoc :soul (:soul a))
                                            (:model a) (assoc :model (:model a)))])
                                    agents))
        models'       (into {} (map (fn [[alias m]]
                                      [alias {:model          (:model m)
                                              :provider       (:provider m)
                                              :context-window (:context-window m)}])
                                    models))
        providers     (into {} (map (fn [[_ m]]
                                      [(:provider m) {:base-url "http://fake"}])
                                    models))
        default-crew  (or (first (keys crew)) "main")
        default-model (or (get-in crew [default-crew :model])
                          (first (keys models')))]
    {:defaults  {:crew default-crew :model default-model}
     :crew      crew
     :models    models'
     :providers providers}))

(defn- with-feature-fs [f]
  (if-let [mem-fs (g/get :mem-fs)]
    (binding [fs/*fs* mem-fs]
      (f))
    (f)))

(defn- resolve-home-path [home]
  (cond
    (str/starts-with? home "/") home
    (and (g/get :state-dir)
         (= home (subs (g/get :state-dir) 1))) (g/get :state-dir)
    :else (str (System/getProperty "user.dir") "/" home)))

(defn -resolve-turn-context [{:keys [agents crew models workspace-home state-dir]} crew-id]
  (with-feature-fs
    #(let [agents (or (not-empty crew) (not-empty agents))
            home   (or state-dir workspace-home)
            cfg    (if agents
                      (build-synthetic-cfg agents models)
                      (config/load-config {:home home}))
            ctx    (config/resolve-crew-context cfg crew-id {:home home})]
       (assoc ctx :boot-files (session-ctx/read-boot-files nil)))))

(defn workspace-soul-md [agent home doc-string]
  (let [abs-home  (resolve-home-path home)
          ws-dir   (str abs-home "/.isaac/workspace-" agent)
          soul-path (str ws-dir "/SOUL.md")]
    (with-feature-fs #(do
                        (fs/mkdirs ws-dir)
                        (fs/spit soul-path (str/trim doc-string)))))
  (g/assoc! :workspace-home (resolve-home-path home)))

(defn turn-context-resolved [agent]
  (g/assoc! :resolved-ctx
            (-resolve-turn-context {:models         (g/get :models)
                                    :agents         (g/get :agents)
                                    :crew           (g/get :crew)
                                    :workspace-home (g/get :workspace-home)
                                    :state-dir      (g/get :state-dir)}
                                   agent)))

(defn resolved-soul-contains [expected]
  (let [soul (:soul (g/get :resolved-ctx))]
    (g/should (str/includes? (or soul "") expected))))

(defn resolved-soul-is [expected]
  (g/should= expected (:soul (g/get :resolved-ctx))))

(defn resolved-model-not-nil []
  (g/should-not-be-nil (:model (g/get :resolved-ctx))))

(defn resolved-provider-not-nil []
  (g/should-not-be-nil (:provider (g/get :resolved-ctx))))

(defgiven "workspace {agent:string} in {home:string} has SOUL.md:" context/workspace-soul-md
  "Writes SOUL.md to <home>/.isaac/workspace-<agent>/SOUL.md and binds
   :workspace-home. The workspace subdirectory pattern is how per-crew
   workspace souls are resolved at turn time.")

(defwhen "turn context is resolved for crew {crew:string}" context/turn-context-resolved
  "Resolves the turn context (soul, model, provider, provider-config)
   for the given crew id. Uses a synthetic cfg built from in-memory
   :crew/:models atoms when present; otherwise loads from disk at
   :workspace-home or :state-dir. Stores result in :resolved-ctx.")

(defthen "the resolved soul contains {expected:string}" context/resolved-soul-contains)

(defthen "the resolved soul is {expected:string}" context/resolved-soul-is)

(defthen "the resolved model is not nil" context/resolved-model-not-nil)

(defthen "the resolved provider is not nil" context/resolved-provider-not-nil)
