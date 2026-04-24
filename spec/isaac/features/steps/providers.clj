(ns isaac.features.steps.providers
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.config.loader :as config]
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
    "openai-api"  "OPENAI_API_KEY"
    "openai-codex" "isaac auth login --provider openai-chatgpt"
    "openai-chatgpt" "isaac auth login --provider openai-chatgpt"
    "grok"        "GROK_API_KEY"
    provider))

(defn- clear-access-error? [provider result]
  (let [message (or (get-in result [:body :error :message])
                    (get-in result [:body :detail])
                    "")]
    (case provider
      "openai" (or (= "insufficient_quota" (get-in result [:body :error :code]))
                    (str/includes? message "quota"))
      "openai-api" (or (= "insufficient_quota" (get-in result [:body :error :code]))
                    (str/includes? message "quota"))
      false)))

(defn- assoc-hyphenated-header [headers name value]
  (let [segments (str/split name #"-")]
    (assoc-in headers (mapv keyword segments) value)))

(defn- provider-config-key [key]
  (keyword key))

(defn- request-for-match [request]
  (update request :headers
          (fn [headers]
            (reduce (fn [acc [name value]]
                      (cond-> acc
                        true                          (assoc (keyword name) value)
                        (str/includes? name "-")     (assoc-hyphenated-header name value)))
                    {}
                    headers))))

(defn- current-provider-request []
  (or (g/get :provider-request)
       (grover/last-provider-request)))

(defn- current-outbound-http-request []
  (or (g/get :outbound-http-request)
      (current-provider-request)))

(defn- outbound-http-requests []
  (or (g/get :outbound-http-requests)
      (when-let [request (current-provider-request)]
        [request])))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given -----

(defgiven provider-configured "the provider {name:string} is configured with:"
  "Writes a provider config into the :provider-configs test atom (not
   disk). Keys are converted kebab→camel via provider-config-key; values
   have ${VAR} substitution applied. Passed as extra-opts to the next
   'isaac is run with'."
  [provider-name table]
  (let [config (into {} (map (fn [row]
                                 (let [m (zipmap (:headers table) row)]
                                   [(provider-config-key (get m "key")) (resolve-env-value (get m "value"))]))
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

(defthen outbound-http-request-matches "the last outbound HTTP request matches:"
  "Awaits the turn future, then matches the last HTTP request Isaac
   sent to any provider. Table uses the match/match-object DSL (dot-path
   in 'key' column)."
  [table]
  (session-steps/await-turn!)
  (let [request (request-for-match (current-outbound-http-request))
        result  (match/match-object table request)]
    (g/should= [] (:failures result))))

(defthen outbound-http-request-to-url-matches "an outbound HTTP request to {url:string} matches:"
  "Filters captured HTTP requests by url, then matches the nth (default
   first). Use a row with key='#index' to select a different position."
  [url table]
  (session-steps/await-turn!)
  (let [requests  (->> (outbound-http-requests)
                       (filter #(= url (:url %)))
                       (mapv request-for-match))
        rows      (cond->> (or (:rows table) [])
                    (and (= 2 (count (:headers table)))
                         (not (#{{"key" "value"} {"path" "value"}}
                               (set (:headers table)))))
                    (cons (:headers table)))
        idx-row   (some #(when (= "#index" (first %)) %) rows)
        idx       (some-> idx-row second parse-long)
        rows'     (vec (remove #(= "#index" (first %)) rows))
        result    (match/match-object {:rows rows'} (nth requests (or idx 0) nil))]
    (g/should= [] (:failures result))))

(defthen provider-request-lacks-path "the last provider request does not contain path {path:string}"
  [path]
  (session-steps/await-turn!)
  (let [request (request-for-match (current-provider-request))]
    (g/should= nil (match/get-path request path))))

(defthen provider-request-has-function-call-output "the last provider request contains a function_call_output item"
  []
  (session-steps/await-turn!)
  (let [input (get-in (current-provider-request) [:body :input])
        item  (some #(when (= "function_call_output" (:type %)) %) input)]
    (g/should-not-be-nil item)
    (g/should (some? (:call_id item)))))

(defthen provider-request-lacks-tool-role "the last provider request does not contain any role:tool input item"
  []
  (session-steps/await-turn!)
  (let [input (get-in (current-provider-request) [:body :input])]
    (g/should-not (some #(= "tool" (:role %)) input))))

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
  "Accepts any of: (:error result) = :auth-failed, HTTP :status 401,
   or :api-error with a 4xx status. Use when the exact error shape
   varies by provider but 'auth failed' is the invariant."
  []
  (session-steps/await-turn!)
  (let [result (g/get :llm-result)]
    (g/should (or (= :auth-failed (:error result))
                  (= 401 (:status result))
                  (and (= :api-error (:error result))
                       (some? (:status result))
                       (>= (:status result) 400))))))

(defthen live-call-or-auth-missing "the live {provider:string} call succeeds or reports missing auth clearly"
  "For integration/live tests. Passes if the provider call succeeded OR
   produced a missing-auth / access-error message that names the right
   env var / credential path. Avoids failing the suite just because
   credentials are absent in CI."
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
