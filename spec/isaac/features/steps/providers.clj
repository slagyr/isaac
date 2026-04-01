(ns isaac.features.steps.providers
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
    [isaac.prompt.anthropic :as anthropic]
    [isaac.session.storage :as storage]))

;; region ----- Helpers -----

(defn- state-dir [] (g/get :state-dir))

(defn- current-key []
  (or (g/get :current-key)
      (:key (first (storage/list-sessions (state-dir) "main")))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given -----

(defgiven provider-configured "the provider {name:string} is configured with:"
  [provider-name table]
  (let [config (into {} (map (fn [row]
                               (let [m (zipmap (:headers table) row)]
                                 [(keyword (get m "key")) (get m "value")]))
                             (:rows table)))]
    (g/update! :provider-configs
               (fn [m] (assoc (or m {}) provider-name config)))))

;; endregion ^^^^^ Given ^^^^^

;; region ----- When -----

(defwhen anthropic-prompt-built "a prompt is built for the Anthropic provider"
  []
  (let [key-str    (current-key)
        transcript (storage/get-transcript (state-dir) key-str)
        agents     (g/get :agents)
        models     (g/get :models)
        agent-id   (:agent (storage/parse-key key-str))
        agent      (get agents agent-id)
        model      (get models (:model agent))
        tools      (g/get :tools)]
    (g/assoc! :prompt (anthropic/build
                        {:model      (:model model)
                         :soul       (:soul agent)
                         :transcript transcript
                         :tools      tools}))))

;; endregion ^^^^^ When ^^^^^

;; region ----- Then -----

(defthen penultimate-user-has-cache "the penultimate user message has cache_control"
  []
  (let [p        (g/get :prompt)
        messages (:messages p)
        user-msgs (->> messages
                       (map-indexed vector)
                       (filter #(= "user" (:role (second %)))))]
    (when (>= (count user-msgs) 2)
      (let [[idx msg] (nth user-msgs (- (count user-msgs) 2))
            content   (:content msg)]
        (if (sequential? content)
          (g/should (some :cache_control content))
          (g/should false))))))

(defthen request-header-present "the request header {header:string} is present"
  [header]
  (let [result  (g/get :llm-result)
        response (or (:response result) result)
        headers (or (:_headers response) (:_headers result))]
    (g/should (get headers header))))

(defthen request-header-matches #"the request header \"(.+)\" matches #\"(.+)\""
  [header pattern]
  (let [result  (g/get :llm-result)
        response (or (:response result) result)
        headers (or (:_headers response) (:_headers result))
        value   (get headers header)]
    (g/should (and value (re-matches (re-pattern pattern) value)))))

(defthen auth-failed "an error is reported indicating authentication failed"
  []
  (let [result (g/get :llm-result)]
    (g/should (or (= :auth-failed (:error result))
                  (= 401 (:status result))))))

;; endregion ^^^^^ Then ^^^^^
