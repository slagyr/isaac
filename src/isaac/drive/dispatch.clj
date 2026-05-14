(ns isaac.drive.dispatch
  (:require
    [clojure.string :as str]
    [isaac.llm.api :as api]
    [isaac.llm.providers :as providers]
    [isaac.llm.registry :as registry]
    [isaac.llm.tool-loop :as tool-loop]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]))

(def built-in-providers registry/built-in-providers)

(def resolve-api api/resolve-api)

(defn- levenshtein [^String s ^String t]
  (let [m (.length s) n (.length t)]
    (if (zero? m)
      n
      (loop [prev (vec (range (inc n))) i 0]
        (if (= i m)
          (peek prev)
          (recur (reduce (fn [row j]
                           (conj row (min (inc (peek row))
                                         (inc (nth prev (inc j)))
                                         (+ (nth prev j)
                                            (if (= (.charAt s i) (.charAt t j)) 0 1)))))
                         [(inc i)]
                         (range n))
                 (inc i)))))))

(defn- did-you-mean [name known-providers]
  (->> known-providers
       (filter #(<= (levenshtein name %) 2))
       (sort-by #(levenshtein name %))
       first))

(defn- unknown-provider-message [provider-name known-providers]
  (let [suggestion (did-you-mean provider-name known-providers)
        known-str  (str/join ", " (sort known-providers))]
    (str "unknown provider \"" provider-name "\""
         (when suggestion (str "; did you mean \"" suggestion "\"?"))
         " — known: " known-str)))

(deftype UnknownApiProvider [provider-name known-providers]
  api/Api
  (chat [_ _] {:error :unknown-provider :message (unknown-provider-message provider-name known-providers)})
  (chat-stream [_ _ _] {:error :unknown-provider :message (unknown-provider-message provider-name known-providers)})
  (followup-messages [_ request _ _ _] (:messages request))
  (config [_] {})
  (display-name [_] provider-name)
  (build-prompt [_ opts] {:model (:model opts) :messages []}))

(defn make-provider
  "Resolve (name, config) to an Api instance via the open registry.
   Each provider impl namespace registers a factory at load time
   (see e.g. isaac.llm.api.anthropic-messages). Returns an UnknownApiProvider
   (whose chat/chat-stream emit an error response) when the api cannot be found."
  [name provider-config]
  (let [[name cfg]   (api/normalize-pair name provider-config)
        module-index (merge (module-loader/core-index) (:module-index cfg))
        known        (sort (distinct (concat (providers/known-providers)
                                             (keys (providers/module-providers module-index)))))
        api-id       (api/resolve-api name cfg)
        factory      (or (api/factory-for api-id)
                         (when-let [module-id (module-loader/supporting-module-id module-index :llm/api api-id)]
                           (module-loader/activate! module-id module-index)
                           (api/factory-for api-id)))]
    (if factory
      (factory name cfg)
      (UnknownApiProvider. name known))))

(defn- response-preview [result]
  (let [content    (or (get-in result [:message :content])
                       (get-in result [:response :message :content]))
        tool-calls (or (get-in result [:message :tool_calls])
                       (get-in result [:response :message :tool_calls]))]
    (cond-> {}
      (string? content) (assoc :content-chars (count content))
      tool-calls (assoc :tool-calls-count (count tool-calls)))))

(defn- log-dispatch-result [provider result error-event response-event]
  (if (:error result)
    (log/error error-event :provider provider :error (:error result) :status (:status result))
    (log/debug response-event (merge {:provider provider :model (:model result)}
                                     (response-preview result))))
  result)

(defn dispatch-chat [p request]
  (let [name (api/display-name p)]
    (log/debug :chat/request :provider name :model (:model request))
    (log-dispatch-result name (api/chat p request) :chat/error :chat/response)))

(defn dispatch-chat-stream [p request on-chunk]
  (let [name (api/display-name p)]
    (log/debug :chat/stream-request :provider name :model (:model request))
    (log-dispatch-result name (api/chat-stream p request on-chunk)
                         :chat/stream-error :chat/stream-response)))

(defn dispatch-chat-with-tools
  "Run a tool-call loop for this api. Composed from Api/chat
   and Api/followup-messages."
  [p request tool-fn]
  (let [name (api/display-name p)]
    (log/debug :chat/request-with-tools :provider name :model (:model request))
    (log-dispatch-result name
                         (tool-loop/run #(api/chat p %)
                                        #(api/followup-messages p %1 %2 %3 %4)
                                         request
                                         tool-fn)
                         :chat/error :chat/response)))
