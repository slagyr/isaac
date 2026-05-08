;; mutation-tested: 2026-05-06
(ns isaac.llm.prompt.anthropic
  (:require
    [isaac.llm.prompt.builder :as builder]))

;; region ----- System Block -----

(defn- build-system [system-text]
  [{:type          "text"
    :text          system-text
    :cache_control {:type "ephemeral"}}])

;; endregion ^^^^^ System Block ^^^^^

;; region ----- Messages -----

(defn- extract-messages
  "Extract conversation messages from transcript, excluding system prompts."
  [soul transcript boot-files]
  (let [raw (builder/build {:boot-files boot-files :model "tmp" :soul soul :transcript transcript})]
    (->> (:messages raw)
         (remove #(= "system" (:role %)))
         (mapv #(select-keys % [:role :content])))))

(defn- penultimate-user-index
  "Find the index of the penultimate user message."
  [messages]
  (let [user-indices (->> messages
                          (map-indexed vector)
                          (filter #(= "user" (:role (second %))))
                          (map first))]
    (when (>= (count user-indices) 2)
      (nth user-indices (- (count user-indices) 2)))))

(defn- apply-cache-breakpoints
  "Add cache_control to the penultimate user message."
  [messages]
  (if-let [idx (penultimate-user-index messages)]
    (update messages idx
            (fn [msg]
              (let [content (:content msg)]
                (if (string? content)
                  (assoc msg :content [{:type          "text"
                                        :text          content
                                        :cache_control {:type "ephemeral"}}])
                  ;; Already content blocks — add cache_control to last block
                  (let [last-idx (dec (count content))]
                    (assoc msg :content
                           (update content last-idx
                                   #(assoc % :cache_control {:type "ephemeral"}))))))))
    messages))

;; endregion ^^^^^ Messages ^^^^^

;; region ----- Tools -----

(defn- build-tools [tools]
  (when (seq tools)
    (mapv (fn [tool]
            {:name         (:name tool)
             :description  (:description tool)
             :input_schema (:parameters tool)})
          tools)))

;; endregion ^^^^^ Tools ^^^^^

;; region ----- Public API -----

(defn build
  "Build an Anthropic Messages API request.
   Options:
     :model      - model string (e.g. \"claude-sonnet-4-6\")
     :soul       - system prompt text
     :transcript - vector of transcript entries
     :tools      - vector of tool definitions (optional)
     :max-tokens - max tokens for response (default 4096)"
  [{:keys [boot-files model soul transcript tools max-tokens]
     :or   {max-tokens 4096}}]
  (let [system-text (if boot-files
                      (str soul "\n\n" boot-files)
                      soul)
        messages    (-> (extract-messages soul transcript boot-files)
                      vec
                      apply-cache-breakpoints)]
    (cond-> {:model      model
              :max_tokens max-tokens
              :system     (build-system system-text)
              :messages   messages}
      (seq tools) (assoc :tools (build-tools tools)))))

;; endregion ^^^^^ Public API ^^^^^
