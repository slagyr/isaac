(ns isaac.features.steps.context
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.context :as session-ctx]))

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
                    (config/load-config {:home home}))]
       (session-ctx/resolve-turn-context {:cfg cfg :home home} crew-id))))

(defgiven workspace-soul-md "workspace {agent:string} in {home:string} has SOUL.md:"
  [agent home doc-string]
  (let [abs-home  (resolve-home-path home)
          ws-dir   (str abs-home "/.isaac/workspace-" agent)
          soul-path (str ws-dir "/SOUL.md")]
    (with-feature-fs #(do
                        (fs/mkdirs ws-dir)
                        (fs/spit soul-path (str/trim doc-string)))))
  (g/assoc! :workspace-home (resolve-home-path home)))

(defwhen turn-context-resolved "turn context is resolved for crew {crew:string}"
  [agent]
  (g/assoc! :resolved-ctx
            (-resolve-turn-context {:models         (g/get :models)
                                    :agents         (g/get :agents)
                                    :crew           (g/get :crew)
                                    :workspace-home (g/get :workspace-home)
                                    :state-dir      (g/get :state-dir)}
                                   agent)))

(defthen resolved-soul-contains "the resolved soul contains {expected:string}"
  [expected]
  (let [soul (:soul (g/get :resolved-ctx))]
    (g/should (str/includes? (or soul "") expected))))

(defthen resolved-soul-is "the resolved soul is {expected:string}"
  [expected]
  (g/should= expected (:soul (g/get :resolved-ctx))))

(defthen resolved-model-not-nil "the resolved model is not nil"
  []
  (g/should-not-be-nil (:model (g/get :resolved-ctx))))

(defthen resolved-provider-not-nil "the resolved provider is not nil"
  []
  (g/should-not-be-nil (:provider (g/get :resolved-ctx))))
