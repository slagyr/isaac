(ns isaac.session.transcript
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]))

(defn content->text [content]
  (cond
    (string? content)
    content

    (and (vector? content) (every? map? content))
    (->> content
         (filter #(= "text" (:type %)))
         (map :text)
         (apply str))

    :else
    nil))

(defn tool-calls [message]
  (cond
    (= "toolCall" (:type message))
    [{:type "toolCall" :id (:id message) :name (:name message) :arguments (:arguments message)}]

    (and (vector? (:content message))
         (= "toolCall" (:type (first (:content message)))))
    (:content message)

    (and (string? (:content message))
         (str/starts-with? (:content message) "["))
    (try
      (let [parsed (json/parse-string (:content message) true)]
        (when (and (sequential? parsed) (= "toolCall" (:type (first parsed))))
          (vec parsed)))
      (catch Exception _
        nil))

    :else
    nil))

(defn first-tool-call [message]
  (first (tool-calls message)))

(defn truncate-tool-result
  "Truncate a tool result string using head-and-tail strategy.
   max-chars defaults to 30% of context-window * 4 (chars per token estimate)."
  [content context-window]
  (let [max-chars (int (* 0.3 context-window 4))
        len       (count content)]
    (if (<= len max-chars)
      content
      (let [half (quot max-chars 2)
            head (subs content 0 half)
            tail (subs content (- len half))]
        (str head "\n\n... [" (- len max-chars) " characters truncated] ...\n\n" tail)))))
