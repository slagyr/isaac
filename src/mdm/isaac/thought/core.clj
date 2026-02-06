(ns mdm.isaac.thought.core
  "Thought persistence - core functions for working with thoughts."
  (:require [c3kit.bucket.api :as db]))

(defn find-by-type [type]
  (db/find-by :thought :type type))

(defn find-similar [embedding limit]
  (db/find :thought
           :order-by {:embedding ['<=> (vec embedding)]}
           :take limit))
