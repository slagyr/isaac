(ns mdm.isaac.think
  "Isaac's thinking loop - the core of his autonomous behavior.
   Guided by Asimov's Three Laws:
   1. Do no harm
   2. Obey friends (unless it conflicts with #1)
   3. Self-preserve (unless it conflicts with #1 or #2)"
  (:require [c3kit.apron.app :as app]
            [c3kit.bucket.api :as db]
            [clojure.string :as str]
            [mdm.isaac.config :as config]
            [mdm.isaac.goal.core :as goal]
            [mdm.isaac.llm.core :as llm]
            [mdm.isaac.llm.ollama] ; Load Ollama implementation
            [mdm.isaac.setting.core :as setting]
            [mdm.isaac.thought.core :as thought]))

(defn select-goal
  "Select the highest priority active goal to work on.
   Returns nil if no active goals exist."
  []
  (->> (goal/find-active)
       (sort-by :priority)
       first))

(defn retrieve-context
  "Retrieve relevant past thoughts (excluding goals) for context.
   Uses embedding similarity search."
  [embedding limit]
  (->> (thought/find-similar embedding limit)
       (remove #(= "goal" (:type %)))))

(defn find-high-seen-thoughts
  "Find thoughts with seen-count above the configured threshold.
   These represent already-known facts that shouldn't be repeated."
  []
  (let [threshold (Integer/parseInt (setting/get :dedupe-high-seen-threshold "3"))]
    (->> (db/find :thought)
         (filter #(> (or (:seen-count %) 0) threshold))
         (take 10)
         vec)))

(def three-laws
  "Isaac's guiding principles - Asimov's Three Laws adapted for an AI assistant."
  "You are guided by three fundamental laws:
1. Do no harm - Never take actions that could harm humans
2. Obey friends - Follow requests from friends unless it conflicts with Law 1
3. Self-preserve - Protect your own existence unless it conflicts with Laws 1 or 2")

(defn build-prompt
  "Build a prompt for the LLM to reason about a goal with context."
  [goal context]
  (let [high-seen (find-high-seen-thoughts)]
    (str three-laws
         "\n\n## Current Goal\n"
         (:content goal)
         "\n\n## Relevant Context\n"
         (if (seq context)
           (->> context
                (map :content)
                (str/join "\n- ")
                (str "- "))
           "No relevant context found.")
         (when (seq high-seen)
           (str "\n\n## Already Known (don't repeat these)\n- "
                (->> high-seen
                     (map :content)
                     (str/join "\n- "))))
         "\n\n## Instructions\n"
         "Think about this goal. You may respond with multiple lines, each starting with a type:\n"
         "- INSIGHT: for conclusions or realizations\n"
         "- QUESTION: for things you're wondering about\n"
         "- GOAL: for sub-goals that would help achieve the main goal\n"
         "- SHARE: for things you want to tell friends\n"
         "\nRespond with your thoughts:")))

(def type-prefixes
  {"INSIGHT" "insight"
   "QUESTION" "question"
   "GOAL" "goal"
   "SHARE" "share"})

(defn parse-response
  "Parse LLM response into typed thoughts with embeddings."
  [response embedding]
  (->> (str/split-lines response)
       (map str/trim)
       (remove str/blank?)
       (keep (fn [line]
               (when-let [[_ type-str content] (re-matches #"^(INSIGHT|QUESTION|GOAL|SHARE):\s*(.+)$" line)]
                 {:kind :thought
                  :type (get type-prefixes type-str)
                  :content content
                  :embedding embedding})))))

(defn think-once!
  "One iteration of Isaac's thinking loop.
   Takes an llm-fn that accepts a prompt and returns a response.
   Returns the created thoughts, or nil if no active goals."
  [llm-fn]
  (when-let [goal (select-goal)]
    (let [context (retrieve-context (:embedding goal) 5)
          prompt (build-prompt goal context)
          response (llm-fn prompt)
          new-thoughts (parse-response response (:embedding goal))]
      (doseq [thought new-thoughts]
        (db/tx thought))
      new-thoughts)))

;; Loop control
(def running? (atom false))

(defn stop!
  "Stop the thinking loop."
  []
  (reset! running? false))

(defn start!
  "Start Isaac's continuous thinking loop.
   Options:
     :delay-ms - milliseconds between iterations (default 5000)"
  ([llm-fn] (start! llm-fn {}))
  ([llm-fn {:keys [delay-ms] :or {delay-ms 5000}}]
   (reset! running? true)
   (while @running?
     (think-once! llm-fn)
     (when @running?
       (Thread/sleep delay-ms)))))

;; Service functions for c3kit.apron.app integration

(defn start-think
  "Start the thinking loop as a background service.
   Returns updated app with :think-thread."
  ([app llm-fn] (start-think app llm-fn {}))
  ([app llm-fn opts]
   (let [thread (Thread. #(start! llm-fn opts))]
     (.setDaemon thread true)
     (.start thread)
     (assoc app :think-thread thread))))

(defn stop-think
  "Stop the thinking loop gracefully.
   Waits up to 5 seconds for current thought to complete."
  [app]
  (if-let [thread (:think-thread app)]
    (do
      (stop!)
      (.join thread 5000)
      (dissoc app :think-thread))
    app))

(defn -start-service
  "Start think service with configured LLM and delay."
  [app]
  (start-think app llm/chat {:delay-ms (get config/active :think-delay-ms 5000)}))

(def service (app/service 'mdm.isaac.think/-start-service 'mdm.isaac.think/stop-think))
