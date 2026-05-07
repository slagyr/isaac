(ns isaac.message.content
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
