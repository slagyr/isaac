(ns isaac.llm.claude-sdk
  (:require
    [babashka.process :as process]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.provider :as provider]))

;; region ----- Model Mapping -----

(defn map-model-alias [model-id]
  (cond
    (str/includes? model-id "sonnet") "sonnet"
    (str/includes? model-id "opus")   "opus"
    (str/includes? model-id "haiku")  "haiku"
    :else                             model-id))

;; endregion ^^^^^ Model Mapping ^^^^^

;; region ----- Prompt Extraction -----

(defn extract-prompt [messages]
  (->> messages
       (filter #(= "user" (:role %)))
       last
       :content))

(defn- extract-system-prompt [request]
  (if-let [system (:system request)]
    (->> system
         (filter #(= "text" (:type %)))
         (map :text)
         (str/join "\n"))
    (let [sys-msg (first (filter #(= "system" (:role %)) (:messages request)))]
      (:content sys-msg))))

;; endregion ^^^^^ Prompt Extraction ^^^^^

;; region ----- Args Building -----

(defn build-args [request {:keys [stream]}]
  (let [model       (map-model-alias (:model request))
        system      (extract-system-prompt request)
        prompt      (extract-prompt (:messages request))
        format      (if stream "stream-json" "json")]
    (cond-> ["-p" "--no-session-persistence"
             "--output-format" format
             "--model" model]
      stream  (conj "--verbose")
      system  (into ["--system-prompt" system])
      true    (conj prompt))))

;; endregion ^^^^^ Args Building ^^^^^

;; region ----- Stream Event Parsing -----

(defn- assistant-text [event]
  (->> (get-in event [:message :content])
       (filter #(= "text" (:type %)))
       (map :text)
       (str/join "")))

(defn parse-stream-event [event acc]
  (case (:type event)
    "assistant"
    (let [text (assistant-text event)]
      (cond-> acc
        (seq text)                       (update :content str text)
        (get-in event [:message :model]) (assoc :model (get-in event [:message :model]))))

    "result"
    (cond-> acc
      (:usage event) (assoc :usage (:usage event)))

    ;; system, rate_limit_event, etc — pass through
    acc))

(defn parse-usage [usage]
  {:input-tokens  (or (:input_tokens usage) 0)
   :output-tokens (or (:output_tokens usage) 0)
   :cache-read    (or (:cache_read_input_tokens usage) 0)
   :cache-write   (or (:cache_creation_input_tokens usage) 0)})

(defn- parse-line [line]
  (try
    (json/parse-string line true)
    (catch Exception _ nil)))

(defn- emit-chunk! [event on-chunk]
  (let [text (assistant-text event)]
    (when (seq text)
      (on-chunk {:delta {:text text}}))))

(defn- process-line [line acc on-chunk]
  (if (str/blank? line)
    acc
    (if-let [event (parse-line line)]
      (do
        (when (= "assistant" (:type event))
          (emit-chunk! event on-chunk))
        (parse-stream-event event acc))
      acc)))

(defn- final-stream-response [acc]
  {:message {:role "assistant" :content (:content acc)}
   :model   (:model acc)
   :usage   (parse-usage (:usage acc))})

(defn- read-stream [reader on-chunk]
  (loop [acc {:content "" :model nil :usage {}}]
    (if-let [line (.readLine reader)]
      (recur (process-line line acc on-chunk))
      (final-stream-response acc))))

;; endregion ^^^^^ Stream Event Parsing ^^^^^

;; region ----- Public API -----

(defn chat
  "Send a non-streaming request via claude CLI. Returns response map."
  [request & [_opts]]
  (try
    (let [args   (build-args request {:stream false})
          cmd    (into ["claude"] args)
          result (apply process/shell {:out :string :err :string :continue true
                                       :in (java.io.ByteArrayInputStream. (byte-array 0))} cmd)
          parsed (json/parse-string (:out result) true)]
      (if (:is_error parsed)
        {:error :sdk-error :message (:result parsed)}
        {:message {:role    "assistant"
                   :content (:result parsed)}
         :model   (or (-> parsed :modelUsage keys first name) (:model request))
         :usage   (parse-usage (:usage parsed))}))
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

(defn chat-stream
  "Send a streaming request via claude CLI. Calls on-chunk for each text delta."
  [request on-chunk & [_opts]]
  (try
    (let [args (build-args request {:stream true})
          cmd  (into ["claude"] args)
          proc (apply process/process {:err :inherit
                                        :in (java.io.ByteArrayInputStream. (byte-array 0))} cmd)]
      (with-open [rdr (io/reader (:out proc))]
        (read-stream rdr on-chunk)))
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

(deftype ClaudeSdkProvider [provider-name cfg]
  provider/Provider
  (chat [_ req] (#'chat req))
  (chat-stream [_ req on-chunk] (#'chat-stream req on-chunk))
  (followup-messages [_ _ _ _ _]
    (throw (ex-info "claude-sdk does not implement followup-messages" {:provider "claude-sdk"})))
  (config [_] cfg)
  (display-name [_] provider-name))

(defn make [name cfg]
  (->ClaudeSdkProvider name cfg))

(defonce _registration
  (provider/register! "claude-sdk" make))

;; endregion ^^^^^ Public API ^^^^^
