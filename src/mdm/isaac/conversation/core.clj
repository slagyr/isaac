(ns mdm.isaac.conversation.core
  "Conversation handling - chat with Isaac."
  (:require [c3kit.bucket.api :as db]
            [clojure.string :as str]))

(defn get-recent-messages
  "Get the N most recent messages from a conversation, in chronological order."
  [conversation-id limit]
  (->> (db/find-by :message :conversation-id conversation-id)
       (sort-by :created-at)
       (take-last limit)
       vec))

(def three-laws
  "Isaac's guiding principles - Asimov's Three Laws adapted for an AI assistant."
  "You are guided by three fundamental laws:
1. Do no harm - Never take actions that could harm humans
2. Obey friends - Follow requests from friends unless it conflicts with Law 1
3. Self-preserve - Protect your own existence unless it conflicts with Laws 1 or 2")

(defn- format-message [msg]
  (let [role-label (if (= :user (:role msg)) "User" "Isaac")]
    (str role-label ": " (:content msg))))

(defn- format-history [messages]
  (when (seq messages)
    (str "\n\n## Conversation History\n"
         (str/join "\n" (map format-message messages)))))

(defn- format-context [thoughts]
  (when (seq thoughts)
    (str "\n\n## Relevant Context\n- "
         (str/join "\n- " (map :content thoughts)))))

(defn build-chat-prompt
  "Build a prompt for chat with conversation history and context."
  [messages context user-message]
  (str three-laws
       (format-history messages)
       (format-context context)
       "\n\n## Current Message\nUser: " user-message
       "\n\nRespond naturally to the user. You may also include thoughts prefixed with:\n"
        "- INSIGHT: for conclusions or realizations\n"
       "- QUESTION: for things you're wondering about\n"
       "\nIsaac:"))

(defn store-message!
  "Store a message in a conversation.
   For Isaac messages, thought-ids can be provided."
  ([conversation-id role content]
   (store-message! conversation-id role content nil))
  ([conversation-id role content thought-ids]
   (db/tx (cond-> {:kind :message
                   :conversation-id conversation-id
                   :role role
                   :content content
                   :created-at (java.util.Date.)}
            thought-ids (assoc :thought-ids thought-ids)))))

(def type-prefixes
  {"INSIGHT" :insight
   "QUESTION" :question})

(defn- parse-thoughts
  "Parse LLM response for thought prefixes."
  [response embedding]
  (->> (str/split-lines response)
       (map str/trim)
       (remove str/blank?)
       (keep (fn [line]
               (when-let [[_ type-str content] (re-matches #"^(INSIGHT|QUESTION):\s*(.+)$" line)]
                 {:kind :thought
                  :type (get type-prefixes type-str)
                  :content content
                  :embedding embedding})))))

(defn- retrieve-context
  "Retrieve relevant thoughts for context using embedding similarity."
  [embedding limit]
  (->> (db/find :thought
                :order-by {:embedding ['<=> (vec embedding)]}
                :take limit)
       (remove #(= :goal (:type %)))))

(defn chat!
  "Handle a chat message from a user.
   Options:
     :llm-fn - function to call LLM (prompt -> response)
     :embed-fn - function to generate embedding (text -> vector)
     :history-limit - number of recent messages to include (default 10)
     :context-limit - number of relevant thoughts to include (default 5)
   Returns {:response \"...\" :thoughts [...]}"
  [conversation-id user-message {:keys [llm-fn embed-fn history-limit context-limit]
                                  :or {history-limit 10 context-limit 5}}]
  (let [;; Store user message
        user-msg (store-message! conversation-id :user user-message)

        ;; Generate embedding for context retrieval
        embedding (embed-fn user-message)

        ;; Gather context
        history (get-recent-messages conversation-id history-limit)
        context (retrieve-context embedding context-limit)

        ;; Build prompt and get response
        prompt (build-chat-prompt history context user-message)
        response (llm-fn prompt)

        ;; Parse response for thoughts
        thoughts (parse-thoughts response embedding)

        ;; Store thoughts with link to user message
        stored-thoughts (mapv #(db/tx (assoc % :source-message-id (:id user-msg))) thoughts)

        ;; Store Isaac's response with thought-ids
        thought-ids (mapv :id stored-thoughts)
        _ (store-message! conversation-id :isaac response thought-ids)]

    {:response response
     :thoughts stored-thoughts}))
