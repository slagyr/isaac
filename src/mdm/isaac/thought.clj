(ns mdm.isaac.thought
  "Thought persistence - re-exports from sub-namespaces for backward compatibility."
  (:require [mdm.isaac.thought.core :as core]
            [mdm.isaac.thought.memory :as memory]
            [mdm.isaac.thought.pg :as pg]))

;; Re-export core multimethods
(def save core/save)
(def find-similar core/find-similar)
(def find-by-type core/find-by-type)

;; Cosine similarity for deduplication
(defn- dot-product [v1 v2]
  (reduce + (map * v1 v2)))

(defn- magnitude [v]
  (Math/sqrt (reduce + (map #(* % %) v))))

(defn cosine-similarity
  "Calculate cosine similarity between two embedding vectors.
   Returns a value between -1 and 1, where 1 means identical."
  [v1 v2]
  (let [mag1 (magnitude v1)
        mag2 (magnitude v2)]
    (if (or (zero? mag1) (zero? mag2))
      0.0
      (/ (dot-product v1 v2) (* mag1 mag2)))))

(defn duplicate?
  "Check if a thought with this embedding would be a duplicate.
   Returns true if any existing thought has similarity >= threshold."
  [embedding threshold]
  (let [similar (find-similar embedding 5)]
    (some #(>= (cosine-similarity embedding (:embedding %)) threshold) similar)))

(defn save-if-unique!
  "Save a thought only if no duplicate exists above the threshold.
   Returns the saved thought, or nil if a duplicate was detected."
  [thought threshold]
  (if (duplicate? (:embedding thought) threshold)
    nil
    (save thought)))

;; Re-export memory functions
(def memory-clear! memory/clear!)

;; Re-export pg functions (new names)
(def create-database pg/create-database)
(def drop-database pg/drop-database)
(def init pg/init)
(def pg-clear! pg/clear!)

;; Backward-compatible aliases (old names)
(def pg-create-database pg/create-database)
(def pg-drop-database pg/drop-database)
(def pg-init pg/init)
