(ns isaac.naming
  (:require
    [clojure.string :as str]
    [isaac.fs :as fs]))

(defprotocol NamedDomain
  "A namespace where generated names might collide."
  (name-taken? [this name]))

(defprotocol NameStrategy
  "An algorithm for producing fresh names."
  (generate [this]))

(defrecord SequentialStrategy [state-dir counter-key prefix fs]
  NameStrategy
  (generate [_]
    (let [counter-file (str state-dir "/" counter-key "/.counter")
          n            (inc (or (when (fs/exists? fs counter-file)
                                  (some-> (fs/slurp fs counter-file) str/trim parse-long))
                                0))]
      (fs/mkdirs fs (fs/parent counter-file))
      (fs/spit fs counter-file (str n))
      (str prefix n))))

(defrecord AdjectiveNounStrategy [domain adjectives nouns]
  NameStrategy
  (generate [_]
    (loop [attempt 0]
      (when (>= attempt 1000)
        (throw (ex-info "AdjectiveNounStrategy: failed to generate a unique name after 1000 attempts" {})))
      (let [candidate (str (rand-nth adjectives) " " (rand-nth nouns))]
        (if (name-taken? domain candidate)
          (recur (inc attempt))
          candidate)))))
