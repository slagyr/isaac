(ns mdm.isaac.thought.dedupe
  "Thought deduplication - detect and handle duplicate thoughts."
  (:require [c3kit.bucket.api :as db]
            [clojure.string :as str]))

(defn find-exact-match
  "Find a thought with exact content match (case-insensitive).
   Returns the matching thought or nil."
  [content]
  (let [lower-content (str/lower-case content)]
    (->> (db/find :thought)
         (filter #(= lower-content (str/lower-case (:content %))))
         first)))

(defn increment-seen-count!
  "Increment the seen-count of a thought. Treats nil as 1."
  [thought]
  (let [current (or (:seen-count thought) 1)]
    (db/tx (assoc thought :seen-count (inc current)))))

(defn find-similar-candidates
  "Find thoughts similar to the given embedding, limited to max-candidates.
   Returns thoughts ordered by similarity (most similar first)."
  [embedding max-candidates]
  (db/find :thought
           :order-by {:embedding ['<=> (vec embedding)]}
           :take max-candidates))

(defn- build-semantic-prompt [new-content candidate-content]
  (str "Are these two thoughts semantically equivalent? Answer YES or NO only.\n\n"
       "Thought 1: " new-content "\n"
       "Thought 2: " candidate-content))

(defn find-semantic-match
  "Find a semantically equivalent thought among candidates using LLM.
   Returns the first matching candidate or nil."
  [new-content candidates llm-fn]
  (some (fn [candidate]
          (let [prompt (build-semantic-prompt new-content (:content candidate))
                response (llm-fn prompt)]
            (when (str/starts-with? (str/upper-case (str/trim response)) "YES")
              candidate)))
        candidates))
