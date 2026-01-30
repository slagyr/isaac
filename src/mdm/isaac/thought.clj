(ns mdm.isaac.thought
  "Thought persistence - re-exports from sub-namespaces for backward compatibility."
  (:require [c3kit.bucket.api :as db]))

(defn find-by-type [type]
  (db/find-by :thought :type type))

(defn find-similar [embedding limit]
  (db/find :thought
           :order-by {:embedding ['<=> (vec embedding)]}
           :take limit))
