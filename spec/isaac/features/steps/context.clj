(ns isaac.features.steps.context
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.config.resolution :as config]
    [isaac.fs :as fs]
    [isaac.session.context :as session-ctx]))

(defn- build-synthetic-cfg [agents models]
  (let [agents-models (into {} (map (fn [[_ m]]
                                      [(keyword (:alias m))
                                       {:model         (:model m)
                                        :provider      (:provider m)
                                        :contextWindow (:contextWindow m)}])
                                    models))
        providers     (distinct
                        (mapv (fn [[_ m]] {:name (:provider m) :baseUrl "http://fake"})
                              models))
        agent-list    (mapv (fn [[id a]]
                              (cond-> {:id id}
                                (:soul a) (assoc :soul (:soul a))
                                (:model a) (assoc :model (:model a))))
                             agents)]
    {:agents {:defaults {}
              :list     agent-list
              :models   agents-models}
     :models {:providers providers}}))

(defgiven workspace-soul-md "workspace {agent:string} in {home:string} has SOUL.md:"
  [agent home doc-string]
  (let [abs-home (if (str/starts-with? home "/")
                   home
                   (str (System/getProperty "user.dir") "/" home))
        ws-dir   (str abs-home "/.isaac/workspace-" agent)
        soul-path (str ws-dir "/SOUL.md")]
    (fs/mkdirs ws-dir)
    (fs/spit soul-path (str/trim doc-string)))
  (g/assoc! :workspace-home (if (str/starts-with? home "/")
                              home
                              (str (System/getProperty "user.dir") "/" home))))

(defwhen turn-context-resolved "turn context is resolved for crew {crew:string}"
  [agent]
  (let [models (g/get :models)
        agents (or (g/get :crew) (g/get :agents))
        home   (or (g/get :workspace-home) (g/get :state-dir))
        cfg    (if agents
                  (build-synthetic-cfg agents models)
                  (config/load-config {:home home}))
        ctx    (session-ctx/resolve-turn-context {:cfg cfg :home home} agent)]
    (g/assoc! :resolved-ctx ctx)))

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
