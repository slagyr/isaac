(ns isaac.features.steps.providers
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.config.resolution :as config]
    [isaac.features.matchers :as match]
    [isaac.features.steps.session :as session-steps]
    [isaac.llm.grover :as grover]
    [isaac.prompt.anthropic :as anthropic]
    [isaac.session.storage :as storage]))

;; region ----- Helpers -----

(defn- state-dir [] (g/get :state-dir))

(defn- current-key []
  (or (g/get :current-key)
      (:key (first (storage/list-sessions (state-dir) "main")))))

(defn- resolve-env-value [value]
  (if (string? value)
    (str/replace value #"\$\{([^}]+)\}" (fn [[_ var-name]] (or (config/env var-name) "")))
    value))

(defn- missing-auth-hint [provider]
  (case provider
    "anthropic"   "ANTHROPIC_API_KEY"
    "openai"      "OPENAI_API_KEY"
    "openai-codex" "isaac auth login --provider openai-codex"
    "grok"        "GROK_API_KEY"
    provider))

(defn- clear-access-error? [provider result]
  (let [message (or (get-in result [:body :error :message])
                    (get-in result [:body :detail])
                    "")]
    (case provider
      "openai" (or (= "insufficient_quota" (get-in result [:body :error :code]))
                    (str/includes? message "quota"))
      false)))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given -----

(defgiven provider-configured "the provider {name:string} is configured with:"
  [provider-name table]
  (let [config (into {} (map (fn [row]
                                (let [m (zipmap (:headers table) row)]
                                  [(keyword (get m "key")) (resolve-env-value (get m "value"))]))
                              (:rows table)))]
    (g/update! :provider-configs
               (fn [m] (assoc (or m {}) provider-name (assoc config :name provider-name))))))

(defgiven claude-code-logged-in "Claude Code is logged in"
  []
  ;; Uses real ~/.claude/.credentials.json — no setup needed
  nil)

;; endregion ^^^^^ Given ^^^^^

;; region ----- Then -----

(defthen request-header-included "the request includes header {header:string}"
  [header]
  (let [result  (g/get :llm-result)
        response (or (:response result) result)
        headers (or (:_headers response) (:_headers result))]
    (g/should (get headers header))))

(defthen provider-request-matches "the last provider request matches:"
  [table]
  (let [request (grover/last-provider-request)
        result  (match/match-object table request)]
    (g/should= [] (:failures result))))

(defthen request-header-matches #"the request header \"(.+)\" matches #\"(.+)\""
  [header pattern]
  (let [result  (g/get :llm-result)
        response (or (:response result) result)
        headers (or (:_headers response) (:_headers result))
        value   (get headers header)]
    (g/should (and value (re-matches (re-pattern pattern) value)))))

(defthen request-header-is "the request header {header:string} is {expected:string}"
  [header expected]
  (let [result   (g/get :llm-result)
        response (or (:response result) result)
        headers  (or (:_headers response) (:_headers result))]
    (g/should= expected (get headers header))))

(defthen auth-failed "an error is reported indicating authentication failed"
  []
  (session-steps/await-turn!)
  (let [result (g/get :llm-result)]
    (g/should (or (= :auth-failed (:error result))
                  (= 401 (:status result))
                  (and (= :api-error (:error result))
                       (some? (:status result))
                       (>= (:status result) 400))))))

(defthen live-call-or-auth-missing "the live {provider:string} call succeeds or reports missing auth clearly"
  [provider]
  (session-steps/await-turn!)
  (let [result (g/get :llm-result)]
    (if (= :auth-missing (:error result))
      (let [message (or (:message result) "")]
        (g/should (str/includes? message (missing-auth-hint provider))))
      (if (clear-access-error? provider result)
        (let [message (or (get-in result [:body :error :message])
                          (get-in result [:body :detail])
                          "")]
          (g/should (str/includes? message "quota")))
        (let [transcript (storage/get-transcript (state-dir) (current-key))
              assistant  (last (filter #(= "assistant" (get-in % [:message :role])) transcript))]
          (g/should-not (:error result))
          (g/should-not-be-nil assistant)
          (g/should= provider (get-in assistant [:message :provider])))))))

;; endregion ^^^^^ Then ^^^^^
